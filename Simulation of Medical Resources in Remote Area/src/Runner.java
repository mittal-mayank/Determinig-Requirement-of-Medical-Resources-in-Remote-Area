import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class Runner {
	private static final int numSimulation = 1000;
	private static final int maxNumDelay = 1000;

	private static ArrayList<Transfer> transfers;

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
		final double maxSeverity;
		final double maxInterArrivalTime;
		final int maxDeaths;
		final double ambulanceSpeed;

		int numAmbulance;

		int deaths;
		SystemState systemState;
		StatisticalCounters statisticalCounters;
		TimeProfile timeProfile;

		double avgNumAmbulance;
		double avgDelay;
		double avgQueueLength;
		double avgPercUtilization;

		Channel1(double rateParameter, double townRadius, double maxSeverity, double maxInterArrivalTime, int maxDeaths,
				double ambulanceSpeed) {
			this.rateParameter = rateParameter;
			this.townRadius = townRadius;
			this.maxSeverity = maxSeverity;
			this.maxInterArrivalTime = maxInterArrivalTime;
			this.maxDeaths = maxDeaths;
			this.ambulanceSpeed = ambulanceSpeed;

			numAmbulance = 1;

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

			Ambulance() {
				patient = null;
				serviceTime = -1;
				delay = -1;
				departureTime = -1;
			}

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
				for (int i = 0; i < numAmbulance; i++) {
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

			int queueLength() {
				return customers.size();
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

			StatisticalCounters() {
				numDelay = 0;
				totalDelay = 0;
				areaBt = 0;
				areaQt = 0;
			}

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
				currClock = 0;
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

			int updateClocks() {
				prevClock = currClock;
				if (nextDepartures.size() == 0) {
					currClock = nextArrival;
					return -1;
				}
				double nextDeparture = nextDepartures.peek().departureTime;
				currClock = Math.min(nextArrival, nextDeparture);
				return (int) (nextArrival - nextDeparture);
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

		int timingRoutine() {
			return timeProfile.updateClocks();
		}

		void arrivalEventRoutine() throws ResetSimulationException, ExitSimulationException {
			if (systemState.updateSeverities()) {
				throw new ResetSimulationException();
			}

			int prevQueueLength = systemState.queueLength();
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

			int prevQueueLength = systemState.queueLength();
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
			avgNumAmbulance += numAmbulance;
			avgDelay += statisticalCounters.totalDelay / statisticalCounters.numDelay;
			avgQueueLength += statisticalCounters.areaQt / timeProfile.currClock;
			avgPercUtilization += (statisticalCounters.areaBt / timeProfile.currClock) * 100;
		}

		void reportGenerator() {
			avgNumAmbulance /= numSimulation;
			avgDelay /= numSimulation;
			avgQueueLength /= numSimulation;
			avgPercUtilization /= numSimulation;
		}

		void mainProgram() {
			for (int i = 0; i < numSimulation; i++) {
				numAmbulance = 1;
				while (true) {
					initializationRoutine();
					try {
						while (true) {
							int event = timingRoutine();
							if (event < 0) {
								arrivalEventRoutine();
							} else {
								departureEventRoutine();
							}
						}
					} catch (ResetSimulationException e) {
						numAmbulance++;
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
			System.out.printf("Number of Ambulances:\t%.4f\n", avgNumAmbulance);
			System.out.printf("Average Delay:\t\t%.4f\n", avgDelay);
			System.out.printf("Average Queue Length:\t%.4f\n", avgQueueLength);
			System.out.printf("Percentage Utilization:\t%.4f\n", avgPercUtilization);
			System.out.println();
		}
	}

	private Runner() {

	}

	public static void run() {
		double rateParameter = 4;
		double townRadius = 30;
		double maxSeverity = 10;
		double maxInterArrivalTime = 0.5;
		int maxDeaths = 5;
		double ambulanceSpeed = 30;
		Channel1 c1 = new Channel1(rateParameter, townRadius, maxSeverity, maxInterArrivalTime, maxDeaths,
				ambulanceSpeed);
		c1.print();
	}

	public static void main(String[] args) {
		Runner.run();
	}
}