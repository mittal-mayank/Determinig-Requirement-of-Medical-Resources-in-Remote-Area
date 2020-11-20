import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class Runner {
	private static final int numSimulation = 1000;
	private static final int maxNumDelay = 1000;

	private static ArrayList<ArrayList<Transfer>> allTransfers;
	private static double maxSeverity;
	private static int maxDeaths;

	private static class Transfer {
		double arrivalTime;
		double severity;

		Transfer(double arrivalTime, double severity) {
			this.arrivalTime = arrivalTime;
			this.severity = severity;
		}
	}

	private static class Channel1 {
		final double rateParameter;
		final double townRadius;
		final double maxInterArrivalTime;
		final double ambulanceSpeed;

		int numAmbulances;

		ArrayList<Transfer> transfers;
		int deaths;
		SystemState systemState;
		StatisticalCounters statisticalCounters;
		TimeProfile timeProfile;

		double avgNumAmbulances;
		double avgDelay;
		double avgQueueLength;
		double avgPercUtilization;

		Channel1(double rateParameter, double townRadius, double maxInterArrivalTime, double ambulanceSpeed) {
			this.rateParameter = rateParameter;
			this.townRadius = townRadius;
			this.maxInterArrivalTime = maxInterArrivalTime;
			this.ambulanceSpeed = ambulanceSpeed;

			numAmbulances = 1;

			mainProgram();
		}

		class Patient {
			final double arrivalTime;
			double severity;
			final double distance;

			Patient() {
				arrivalTime = timeProfile.nextArrival;
				severity = exponentialLibraryRoutine() * maxSeverity;
				while (severity > maxSeverity) {
					severity = exponentialLibraryRoutine() * maxSeverity;
				}
				distance = uniformLibraryRoutine() * townRadius;
			}
		}

		class Ambulance {
			Patient patient;
			double serviceTime;
			double delay;
			double departureTime;

			void admitPatient(Patient patient) {
				this.patient = patient;
				serviceTime = 2 * patient.distance / ambulanceSpeed;
				double timeServiceBegins = Math.max(patient.arrivalTime, departureTime);
				delay = timeServiceBegins - patient.arrivalTime;
				departureTime = timeServiceBegins + serviceTime;
			}
		}

		class SystemState {
			PriorityQueue<Patient> customers;
			ArrayList<Ambulance> servers;

			SystemState() {
				customers = new PriorityQueue<Patient>(new Comparator<Patient>() {

					@Override
					public int compare(Patient patient1, Patient patient2) {
						if (patient1.severity > patient2.severity) {
							return -1;
						} else if (patient1.severity < patient2.severity) {
							return 1;
						} else {
							return 0;
						}
					}

				});
				servers = new ArrayList<Ambulance>();
				for (int i = 0; i < numAmbulances; i++) {
					servers.add(new Ambulance());
				}
			}

			boolean updateSeverities() {
				double eventInterval = timeProfile.currClock - timeProfile.prevClock;
				ArrayList<Patient> toRemove = new ArrayList<Patient>();
				for (Patient patient : customers) {
					patient.severity += eventInterval;
					if (patient.severity > maxSeverity) {
						deaths++;
						if (deaths > maxDeaths) {
							return true;
						}
						toRemove.add(patient);
					}
				}
				customers.removeAll(toRemove);
				return false;
			}

			Ambulance addPatient() {
				Patient patient = new Patient();
				customers.add(patient);
				for (Ambulance ambulance : servers) {
					if (ambulance.patient == null) {
						ambulance.admitPatient(customers.poll());
						return ambulance;
					}
				}
				return null;
			}

			double serverStatus() {
				int total = 0;
				for (Ambulance ambulance : servers) {
					if (ambulance.patient != null) {
						total++;
					}
				}
				return (double) total / servers.size();
			}

			Ambulance removePatient() {
				Ambulance ambulance = timeProfile.nextDepartures.peek();
				transfers.add(new Transfer(ambulance.departureTime, ambulance.patient.severity));
				ambulance.patient = null;
				if (customers.size() != 0) {
					ambulance.admitPatient(customers.poll());
					return ambulance;
				}
				return null;
			}
		}

		class StatisticalCounters {
			int numDelay;
			double totalDelay;
			double areaQt;
			double areaBt;

			void updateStatisticalCounters(Ambulance ambulance, int prevQueueLength, double prevServerStatus) {
				if (ambulance != null) {
					numDelay++;
					totalDelay += ambulance.delay;
				}
				double eventInterval = timeProfile.currClock - timeProfile.prevClock;
				areaQt += prevQueueLength * eventInterval;
				areaBt += prevServerStatus * eventInterval;
			}
		}

		class TimeProfile {
			double prevClock;
			double currClock;
			double nextArrival;
			PriorityQueue<Ambulance> nextDepartures;

			TimeProfile() {
				prevClock = -1;
				nextArrival = uniformLibraryRoutine() * maxInterArrivalTime;
				nextDepartures = new PriorityQueue<Ambulance>(new Comparator<Ambulance>() {

					@Override
					public int compare(Ambulance ambulance1, Ambulance ambulance2) {
						if (ambulance1.departureTime > ambulance2.departureTime) {
							return 1;
						} else if (ambulance1.departureTime < ambulance2.departureTime) {
							return -1;
						} else {
							return 0;
						}
					}

				});
			}

			double updateClocks() {
				prevClock = currClock;
				if (nextDepartures.size() == 0) {
					currClock = nextArrival;
					return -1;
				}
				double nextDeparture = nextDepartures.peek().departureTime;
				currClock = Math.min(nextArrival, nextDeparture);
				return nextArrival - nextDeparture;
			}

			void updateForArrival(Ambulance ambulance) {
				nextArrival += uniformLibraryRoutine() * maxInterArrivalTime;
				if (ambulance != null) {
					nextDepartures.add(ambulance);
				}
			}

			void updateForDeparture(Ambulance ambulance) {
				nextDepartures.poll();
				if (ambulance != null) {
					nextDepartures.add(ambulance);
				}
			}
		}

		class ExitSimulationException extends Exception {
			static final long serialVersionUID = 1L;
		}

		class ResetSimulationException extends Exception {
			static final long serialVersionUID = 1L;
		}

		void initializationRoutine() {
			transfers = new ArrayList<Transfer>();
			deaths = 0;
			systemState = new SystemState();
			statisticalCounters = new StatisticalCounters();
			timeProfile = new TimeProfile();
		}

		double timingRoutine() {
			return timeProfile.updateClocks();
		}

		void arrivalEventRoutine() throws ResetSimulationException, ExitSimulationException {
			if (systemState.updateSeverities()) {
				throw new ResetSimulationException();
			}

			int prevQueueLength = systemState.customers.size();
			double prevServerStatus = systemState.serverStatus();
			Ambulance ambulance = systemState.addPatient();

			timeProfile.updateForArrival(ambulance);
			statisticalCounters.updateStatisticalCounters(ambulance, prevQueueLength, prevServerStatus);

			if (statisticalCounters.numDelay == maxNumDelay) {
				throw new ExitSimulationException();
			}
		}

		void departureEventRoutine() throws ResetSimulationException, ExitSimulationException {
			if (systemState.updateSeverities()) {
				throw new ResetSimulationException();
			}

			int prevQueueLength = systemState.customers.size();
			double prevServerStatus = systemState.serverStatus();
			Ambulance ambulance = systemState.removePatient();

			timeProfile.updateForDeparture(ambulance);
			statisticalCounters.updateStatisticalCounters(ambulance, prevQueueLength, prevServerStatus);

			if (statisticalCounters.numDelay == maxNumDelay) {
				throw new ExitSimulationException();
			}
		}

		double uniformLibraryRoutine() {
			return Math.random();
		}

		double exponentialLibraryRoutine() {
			return Math.log(1 - Math.random()) / (-rateParameter);
		}

		void calculateCounters() {
			avgNumAmbulances += numAmbulances;
			avgDelay += statisticalCounters.totalDelay / statisticalCounters.numDelay;
			avgQueueLength += statisticalCounters.areaQt / timeProfile.currClock;
			avgPercUtilization += (statisticalCounters.areaBt / timeProfile.currClock) * 100;
			allTransfers.add(transfers);
		}

		void reportGenerator() {
			avgNumAmbulances /= numSimulation;
			avgDelay /= numSimulation;
			avgQueueLength /= numSimulation;
			avgPercUtilization /= numSimulation;
		}

		void mainProgram() {
			for (int i = 0; i < numSimulation; i++) {
				numAmbulances = 1;
				while (true) {
					initializationRoutine();
					try {
						while (true) {
							double event = timingRoutine();
							if (event < 0) {
								arrivalEventRoutine();
							} else {
								departureEventRoutine();
							}
						}
					} catch (ResetSimulationException e) {
						numAmbulances++;
						continue;
					} catch (ExitSimulationException e) {
						break;
					}
				}
				calculateCounters();
			}
			reportGenerator();
		}

		void print() {
			System.out.println("Channel 1:-");
			System.out.printf("Number of Ambulances:\t%.4f\n", avgNumAmbulances);
			System.out.printf("Average Delay:\t\t%.4f\n", avgDelay);
			System.out.printf("Average Queue Length:\t%.4f\n", avgQueueLength);
			System.out.printf("Percentage Utilization:\t%.4f\n", avgPercUtilization);
			System.out.println();
		}
	}

	private static class Channel2 {
		int numBeds;
		ArrayList<Transfer> transfers;

		int deaths;
		SystemState systemState;
		StatisticalCounters statisticalCounters;
		TimeProfile timeProfile;

		double avgNumBeds;
		double avgDelay;
		double avgQueueLength;
		double avgPercUtilization;

		Channel2() {
			numBeds = 1;

			mainProgram();
		}

		class Patient {
			final double arrivalTime;
			double severity;

			Patient() {
				arrivalTime = timeProfile.nextArrival;
				severity = transfers.get(timeProfile.arrivalPos).severity;
			}
		}

		class Bed {
			Patient patient;
			double serviceTime;
			double delay;
			double departureTime;

			void admitPatient(Patient patient) {
				this.patient = patient;
				serviceTime = patient.severity;
				double timeServiceBegins = Math.max(patient.arrivalTime, departureTime);
				delay = timeServiceBegins - patient.arrivalTime;
				departureTime = timeServiceBegins + serviceTime;
			}
		}

		class SystemState {
			PriorityQueue<Patient> customers;
			ArrayList<Bed> servers;

			SystemState() {
				customers = new PriorityQueue<Patient>(new Comparator<Patient>() {

					@Override
					public int compare(Patient patient1, Patient patient2) {
						if (patient1.severity > patient2.severity) {
							return -1;
						} else if (patient1.severity < patient2.severity) {
							return 1;
						} else {
							return 0;
						}
					}

				});
				servers = new ArrayList<Bed>();
				for (int i = 0; i < numBeds; i++) {
					servers.add(new Bed());
				}
			}

			boolean updateSeverities() {
				double eventInterval = timeProfile.currClock - timeProfile.prevClock;
				ArrayList<Patient> toRemove = new ArrayList<Patient>();
				for (Patient patient : customers) {
					patient.severity += eventInterval;
					if (patient.severity > maxSeverity) {
						deaths++;
						if (deaths > maxDeaths) {
							return true;
						}
						toRemove.add(patient);
					}
				}
				customers.removeAll(toRemove);
				return false;
			}

			Bed addPatient() {
				Patient patient = new Patient();
				customers.add(patient);
				for (Bed bed : servers) {
					if (bed.patient == null) {
						bed.admitPatient(customers.poll());
						return bed;
					}
				}
				return null;
			}

			double serverStatus() {
				int total = 0;
				for (Bed bed : servers) {
					if (bed.patient != null) {
						total++;
					}
				}
				return (double) total / servers.size();
			}

			Bed removePatient() {
				Bed bed = timeProfile.nextDepartures.peek();
				bed.patient = null;
				if (customers.size() != 0) {
					bed.admitPatient(customers.poll());
					return bed;
				}
				return null;
			}
		}

		class StatisticalCounters {
			int numDelay;
			double totalDelay;
			double areaQt;
			double areaBt;

			void updateStatisticalCounters(Bed bed, int prevQueueLength, double prevServerStatus) {
				if (bed != null) {
					numDelay++;
					totalDelay += bed.delay;
				}
				double eventInterval = timeProfile.currClock - timeProfile.prevClock;
				areaQt += prevQueueLength * eventInterval;
				areaBt += prevServerStatus * eventInterval;
			}
		}

		class TimeProfile {
			double prevClock;
			double currClock;
			int arrivalPos;
			double nextArrival;
			PriorityQueue<Bed> nextDepartures;

			TimeProfile() {
				prevClock = -1;
				nextArrival = transfers.get(arrivalPos).arrivalTime;
				nextDepartures = new PriorityQueue<Bed>(new Comparator<Bed>() {

					@Override
					public int compare(Bed bed1, Bed bed2) {
						if (bed1.departureTime > bed2.departureTime) {
							return 1;
						} else if (bed1.departureTime < bed2.departureTime) {
							return -1;
						} else {
							return 0;
						}
					}

				});
			}

			double updateClocks() {
				prevClock = currClock;
				if (nextDepartures.size() == 0) {
					currClock = nextArrival;
					return -1;
				}
				double nextDeparture = nextDepartures.peek().departureTime;
				currClock = Math.min(nextArrival, nextDeparture);
				return nextArrival - nextDeparture;
			}

			void updateForArrival(Bed bed) {
				arrivalPos++;
				nextArrival = transfers.get(arrivalPos).arrivalTime;
				if (bed != null) {
					nextDepartures.add(bed);
				}
			}

			void updateForDeparture(Bed bed) {
				nextDepartures.poll();
				if (bed != null) {
					nextDepartures.add(bed);
				}
			}
		}

		class ExitSimulationException extends Exception {
			static final long serialVersionUID = 1L;
		}

		class ResetSimulationException extends Exception {
			static final long serialVersionUID = 1L;
		}

		void initializationRoutine() {
			deaths = 0;
			systemState = new SystemState();
			statisticalCounters = new StatisticalCounters();
			timeProfile = new TimeProfile();
		}

		double timingRoutine() {
			return timeProfile.updateClocks();
		}

		void arrivalEventRoutine() throws ResetSimulationException, ExitSimulationException {
			if (systemState.updateSeverities()) {
				throw new ResetSimulationException();
			}

			int prevQueueLength = systemState.customers.size();
			double prevServerStatus = systemState.serverStatus();
			Bed bed = systemState.addPatient();

			if (timeProfile.arrivalPos == transfers.size() - 1) {
				throw new ExitSimulationException();
			}

			timeProfile.updateForArrival(bed);
			statisticalCounters.updateStatisticalCounters(bed, prevQueueLength, prevServerStatus);
		}

		void departureEventRoutine() throws ResetSimulationException, ExitSimulationException {
			if (systemState.updateSeverities()) {
				throw new ResetSimulationException();
			}

			int prevQueueLength = systemState.customers.size();
			double prevServerStatus = systemState.serverStatus();
			Bed bed = systemState.removePatient();

			if (timeProfile.arrivalPos == transfers.size() - 1) {
				throw new ExitSimulationException();
			}

			timeProfile.updateForDeparture(bed);
			statisticalCounters.updateStatisticalCounters(bed, prevQueueLength, prevServerStatus);
		}

		void calculateCounters() {
			avgNumBeds += numBeds;
			avgDelay += statisticalCounters.totalDelay / statisticalCounters.numDelay;
			avgQueueLength += statisticalCounters.areaQt / timeProfile.currClock;
			avgPercUtilization += (statisticalCounters.areaBt / timeProfile.currClock) * 100;
		}

		void reportGenerator() {
			avgNumBeds /= numSimulation;
			avgDelay /= numSimulation;
			avgQueueLength /= numSimulation;
			avgPercUtilization /= numSimulation;
		}

		void mainProgram() {
			for (int i = 0; i < numSimulation; i++) {
				numBeds = 1;
				transfers = allTransfers.get(i);
				while (true) {
					initializationRoutine();
					try {
						while (true) {
							double event = timingRoutine();
							if (event < 0) {
								arrivalEventRoutine();
							} else {
								departureEventRoutine();
							}
						}
					} catch (ResetSimulationException e) {
						numBeds++;
						continue;
					} catch (ExitSimulationException e) {
						break;
					}
				}
				calculateCounters();
			}
			reportGenerator();
		}

		void print() {
			System.out.println("Channel 2:-");
			System.out.printf("Number of Beds:\t\t%.4f\n", avgNumBeds);
			System.out.printf("Average Delay:\t\t%.4f\n", avgDelay);
			System.out.printf("Average Queue Length:\t%.4f\n", avgQueueLength);
			System.out.printf("Percentage Utilization:\t%.4f\n", avgPercUtilization);
			System.out.println();
		}
	}

	private Runner() {
	}

	public static void run() {
		allTransfers = new ArrayList<ArrayList<Transfer>>();
		maxSeverity = 10;
		maxDeaths = 5;

		double rateParameter = 4;
		double townRadius = 30;
		double maxInterArrivalTime = 0.5;
		double ambulanceSpeed = 30;

		Channel1 c1 = new Channel1(rateParameter, townRadius, maxInterArrivalTime, ambulanceSpeed);
		c1.print();

		Channel2 c2 = new Channel2();
		c2.print();
	}

	public static void example() {
		maxSeverity = 10;
		maxDeaths = 5;

		double rateParameter = 4;
		double townRadius = 30;
		double ambulanceSpeed = 30;

		for (double i = 0.1; i <= 2.01; i += 0.01) {
			allTransfers = new ArrayList<ArrayList<Transfer>>();

			double maxInterArrivalTime = i;

			Channel1 c1 = new Channel1(rateParameter, townRadius, maxInterArrivalTime, ambulanceSpeed);
			Channel2 c2 = new Channel2();
			System.out.printf("%.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f\n", maxInterArrivalTime,
					c1.avgNumAmbulances, c1.avgDelay, c1.avgPercUtilization, c1.avgQueueLength, c2.avgNumBeds,
					c2.avgDelay, c2.avgPercUtilization, c2.avgQueueLength);
		}
	}

	public static void main(String[] args) {
		Runner.run();
//		Runner.example();
	}
}