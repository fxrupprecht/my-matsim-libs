package org.matsim.contrib.bicycle.run;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;

import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.bicycle.network.ElevationDataParser;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Elevation part is based on bicycle contrib
 * @author frupprecht
 */

public class RunNetworkReaderMultimodalElevation {

	public static void main(String[] args) {

		// ------- Paths ----------

		Path osmDataPath = Paths.get("C:/Users/frupprecht/git/dynamic-demand-sampling/franz/input/MATSim/vienna/data/geofabrik/austria-260407-filtered-wien.osm.pbf");
	    String elevationTiff = "C:/Users/frupprecht/git/dynamic-demand-sampling/franz/input/MATSim/vienna/data/dgm/dgm_wien.tif";
		String outputDir = "C:/Users/frupprecht/git/dynamic-demand-sampling/franz/input/MATSim/vienna/geofabrikMultimodalElevation.xml.gz";
		
		

		// ------- CRS Transformation ----------
		String crsTiff = "EPSG:31287"; 
		String crs = "EPSG:25833";
		
		CoordinateTransformation crsTransformation = TransformationFactory.getCoordinateTransformation(
				TransformationFactory.WGS84, crs);     

		
		

		// ------- Read Network ----------
		
		ElevationDataParser elevationParser = new ElevationDataParser(elevationTiff, crs);

		Network network = new OsmBicycleReader.Builder()
				.setCoordinateTransformation(crsTransformation)
				.setAfterLinkCreated((link, osmTags, direction) -> {
					String hw = osmTags.get("highway");
					Set<String> tagListCar = Set.of(
							"motorway","motorway_link","trunk","trunk_link",
							"primary","primary_link","secondary","secondary_link",
							"tertiary","tertiary_link","residential","unclassified","living_street"
							);
					Set<String> tagListBike = Set.of(
							"primary","primary_link","secondary","secondary_link",
							"tertiary","tertiary_link","residential","unclassified",
							"living_street","track","cycleway"
							);
					Set<String> tagListWalk = Set.of(
							"primary","primary_link","secondary","secondary_link",
							"tertiary","tertiary_link","residential","unclassified",
							"living_street","track","cycleway","pedestrian"
							);

					Set<String> allowed = new java.util.HashSet<>();
					if (hw != null) {
						if (tagListCar.contains(hw))  allowed.add(org.matsim.api.core.v01.TransportMode.car);
						if (tagListBike.contains(hw)) allowed.add(org.matsim.api.core.v01.TransportMode.bike);
						if (tagListWalk.contains(hw)) allowed.add(org.matsim.api.core.v01.TransportMode.walk);
					}
					link.setAllowedModes(allowed);

                    addElevationIfNecessary(link.getFromNode(), elevationParser);
                    addElevationIfNecessary(link.getToNode(), elevationParser);
                })
				
				
				.build()
				.read(osmDataPath);



		// ------- Remove Links with no mode ----------

		for (var l : network.getLinks().values()) {
			var modes = l.getAllowedModes();
			if (modes == null || modes.isEmpty()) {
				network.removeLink(l.getId());
			} 
		}



		// ------- Clean and Write ----------

		new NetworkCleanerModeSpecific().run(network);
		new NetworkWriter(network).write(outputDir);




		// ------- Analyze Network ----------

//		NetworkAnalyzer.analyzeModes(network);
//		NetworkAnalyzer.analyzeOsmTags(network);
//		NetworkAnalyzer.analyzeLength(network);
	}
	
	
	
    private static synchronized void addElevationIfNecessary(Node node, ElevationDataParser elevationParser) {

        if (!node.getCoord().hasZ()) {
            var elevation = elevationParser.getElevation(node.getCoord());
            var newCoord = CoordUtils.createCoord(node.getCoord().getX(), node.getCoord().getY(), elevation);
            // I think it should work to replace the coord on the node reference, since the network only stores references
            // to the node and the internal quad tree only references the x,y-values and the node. janek 4.2020
            node.setCoord(newCoord);
        }
    }
}