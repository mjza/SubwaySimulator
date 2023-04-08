import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.Random;
import java.io.FileWriter;
import java.io.File;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class SubwaySimulator {
	// Output folder 
	private static String output; 
	// Data structure for storing station information
	private static Map<String, Station> stations = new HashMap<>();
	private static Map<String, Station> endingStations = new HashMap<>();
	// Data structure for storing train information
	private static Train[] trains = new Train[12];

	public static void main(String[] args) {
		// Parse the CSV file and store station information
		try {
			int inIndex = Arrays.asList(args).indexOf("--in");
			if(inIndex >= 0 )
				parseStations(args[inIndex + 1]);
			else
				parseStations("./data/subway.csv");
			// output folder 
			int outIndex = Arrays.asList(args).indexOf("--out");
			if(outIndex >= 0)
				output = args[outIndex + 1];
			else
				output = "./out/";
			if(!output.endsWith("\\") && !output.endsWith("/")) {
				output += "/";
			}
			Files.createDirectories(Paths.get(output));
		} catch (CsvValidationException | NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		cleanOutput();
		// Initialize the positions of the 12 trains
		initializeTrains();
		// Simulate the movement of the trains every 15 seconds
		while (true) {
			simulateTrains();
			printTrainPositions();
			writeTrainStatusToCSV();
			try {
				Thread.sleep(15000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void cleanOutput() {
		File folder = new File(output);
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".csv")) {
                file.delete();
            }
        }
		
	}

	private static void parseStations(String filename) throws CsvValidationException, NumberFormatException {
		try {
			CSVReader reader = new CSVReader(new FileReader(filename));
			String[] line;
			line = reader.readNext();// Skip the first line
			while ((line = reader.readNext()) != null) {
				// Parse the CSV line and store the station information in the data structure
				String lineName = line[1];
				int stationNumber = Integer.parseInt(line[2]);
				String stationCode = line[3];
				String stationName = line[4];
				double x = Double.parseDouble(line[5]);
				double y = Double.parseDouble(line[6]);
				Station station = new Station(lineName, stationNumber, stationCode, stationName, x, y);
				stations.put(stationCode, station);
			}
			reader.close();
			markStartEndStations();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void markStartEndStations() {
		for (String lineName : new String[] { "R", "B", "G" }) {
			Supplier<Stream<Entry<String, Station>>> streamSupplier = () -> stations.entrySet().stream()
					.filter(code -> code.getKey().startsWith(lineName)).sorted(Comparator.comparing(Entry::getKey));
			long count = streamSupplier.get().count();
			Station firstStation = streamSupplier.get().findFirst().get().getValue();
			Station lastStation = streamSupplier.get().skip(count - 1).findFirst().get().getValue();
			endingStations.put(lineName + "_start", firstStation);
			endingStations.put(lineName + "_end", lastStation);
		}
	}

	private static Train getTrain(String lineName, int trainNumber) {
		for (Train train : trains) {
			if (train.lineName.equals(lineName) && train.trainNumber == trainNumber) {
				return train;
			}
		}
		return null;
	}

	private static Train getTrainInFront(Train train) {
		for (Train otherTrain : trains) {
			if (otherTrain.lineName.equals(train.lineName) && otherTrain.direction.equals(train.direction)
					&& otherTrain.position > train.position) {
				return otherTrain;
			}
		}
		return null;
	}

	private static Train getTrainBehind(Train train) {
		for (Train otherTrain : trains) {
			if (otherTrain.lineName.equals(train.lineName) && otherTrain.direction.equals(train.direction)
					&& otherTrain.position < train.position) {
				return otherTrain;
			}
		}
		return null;
	}

	private static void initializeTrains() {
		// Initialize the positions of the 12 trains
		int trainNumber = 1;
		for (String lineName : new String[] { "R", "B", "G" }) {
			String[] stationCodes = stations.keySet().stream().filter(code -> code.startsWith(lineName)).sorted()
					.toArray(String[]::new);
			Random random = new Random((Date.from(Instant.now())).getTime());
			for (int i = random.nextInt(stationCodes.length / 3), j = i; i <= j + 5; i += 5) {
				String stationCode = stationCodes[i];
				trains[trainNumber - 1] = new Train(lineName, trainNumber, "forward", stationCode);
				trainNumber++;
			}
			for (int i = stationCodes.length - 5; i < stationCodes.length; i += 4) {
				String stationCode = stationCodes[i];
				trains[trainNumber - 1] = new Train(lineName, trainNumber, "backward", stationCode);
				trainNumber++;
			}
		}
	}

	private static void simulateTrains() {
		// Simulate the movement of the 12 trains every 15 seconds based on their
		// current positions and directions
		for (Train train : trains) {
			if (train.direction.equals("forward")) {
				Train trainInFront = getTrainInFront(train);
				if (trainInFront == null || trainInFront.position - train.position >= 4) {
					train.moveForward();
				}
			} else {
				Train trainBehind = getTrainBehind(train);
				if (trainBehind == null || train.position - trainBehind.position >= 4) {
					train.moveBackward();
				}
			}
		}
	}

	private static void printTrainPositions() {
		// Print out the current positions of the 12 trains on the subway lines to the console.
		System.out.println("Train positions:");
		String[] lines = new String[] { "R", "B", "G" };
		for (int j = 0; j < lines.length; j++) {
			String lineName = lines[j];
			System.out.print(lineName + ": ");
			for (int i = j * 4 + 1; i < j * 4 + 5; i++) {
				Train train = getTrain(lineName, i);
				if (null != train) {
					System.out.print("T" + train.trainNumber + "(" + train.station.stationCode +", " + ("forward".equals(train.direction)? "F" : "B") + ")" );
					if(i < j * 4 + 4)
						System.out.print(", ");
				}

			}
			System.out.println();
		}
	}
	
	private static void writeTrainStatusToCSV() {
	    try {	    	
	    	long timestamp = Date.from(Instant.now()).getTime();
	        FileWriter writer = new FileWriter(output + "Trains_" + timestamp + ".csv");
	        writer.append(Train.columns() + "\n");
	        for (int i = 0; i < 12; i++) {
	            Train train = trains[i];
	            writer.append(train.toString() + "\n");
	        }
	        writer.flush();	        
	        writer.close();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	
	

	// Data structure for storing station information
	private static class Station {
		String lineName;
		int stationNumber;
		String stationCode;
		String stationName;
		double x;
		double y;

		public Station(String lineName, int stationNumber, String stationCode, String stationName, double x, double y) {
			this.lineName = lineName;
			this.stationNumber = stationNumber;
			this.stationCode = stationCode;
			this.stationName = stationName;
			this.x = x;
			this.y = y;
		}
	}

	// Data structure for storing train information
	private static class Train {
		String lineName;
		int trainNumber;
		String direction;
		Station station;
		int position;

		public Train(String lineName, int trainNumber, String direction, String stationCode) {
			this.lineName = lineName;
			this.trainNumber = trainNumber;
			this.direction = direction;
			this.station = stations.get(stationCode);
			this.position = stations.get(stationCode).stationNumber;
		}

		public void moveForward() {

			if (position < endingStations.get(lineName + "_end").stationNumber) {
				position++;
				String code = lineName + (position < 10 ? "0" : "") + position;
				station = stations.get(code);
			} else {
				direction = "backward";
			}
		}

		public void moveBackward() {
			if (position > endingStations.get(lineName + "_start").stationNumber) {
				position--;
				String code = lineName + (position < 10 ? "0" : "") + position;
				station = stations.get(code);
			} else {
				direction = "forward";
			}
		}

		public String toString() {
			String str = "";
			str += lineName;
			str += "," + trainNumber;
			str += "," + station.stationCode;
			str += "," + direction;
			str += "," + ("forward".equals(direction) ? endingStations.get(lineName + "_end") : endingStations.get(lineName + "_start")).stationCode;
			return str;
		}
		
		public static String columns() {
			String str = "";
			str += "LineName";
			str += ",TrainNumber";
			str += ",StationCode";
			str += ",Direction";
			str += ",Destination";			
			return str;
		}
	}
}
