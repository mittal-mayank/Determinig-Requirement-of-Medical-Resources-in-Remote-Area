import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

public class Channel1 {
	private static final int numSimulation = 10000;
	private static final int maxNumDelay = 1000;
	private final double rateParameter;
	private final double townRadius;
	private final double maxSeverity;
	private final double maxInterArrivalTime;
	private final int maxDeaths;
	private final double ambulanceSpeed;

	private int deaths;
	private int numAmbulance;
	private ArrayList<Transfer> transfers;

	private SystemState systemState;
	private StatisticalCounters statisticalCounters;
	private TimeProfile timeProfile;

	private double avgNumAmbulance;
	private double avgDelay;
	private double avgQueueLength;
	private double avgPercUtilization;

	public Channel1(double rateParameter, double townRadius, double maxSeverity, double maxInterArrivalTime,
			int maxDeaths, double ambulanceSpeed) {
		this.rateParameter = rateParameter;
		this.townRadius = townRadius;
		this.maxSeverity = maxSeverity;
		this.maxInterArrivalTime = maxInterArrivalTime;
		this.maxDeaths = maxDeaths;
		this.ambulanceSpeed = ambulanceSpeed;

		deaths = 0;
		numAmbulance = 1;
		transfers = new ArrayList<Transfer>();

		systemState = null;
		statisticalCounters = null;
		timeProfile = null;

		avgNumAmbulance = 0;
		avgDelay = 0;
		avgQueueLength = 0;
		avgPercUtilization = 0;

		mainProgram();
	}

	private class Patient {
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

	private class Ambulance {
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

	private class SystemState {
		PriorityQueue<Patient> customers;
		ArrayList<Ambulance> servers;

		SystemState() {
			customers = new PriorityQueue<Patient>(new Comparator<Patient>() {

				@Override
				public int compare(Patient patient1, Patient patient2) {
					return (int) (patient2.severity - patient1.severity);
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
			ambulance.patient = null;
			if (customers.size() != 0) {
				ambulance.admitPatient(customers.poll());
				return ambulance;
			}
			return null;
		}

	}

	private class StatisticalCounters {
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

	private class TimeProfile {
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
					return (int) (ambulance1.departureTime - ambulance2.departureTime);
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
				transfers.add(new Transfer(ambulance.departureTime, ambulance.patient.severity));
			}
		}

		void updateForDeparture(Ambulance ambulance) {
			nextDepartures.poll();
			if (ambulance != null) {
				nextDepartures.add(ambulance);
				transfers.add(new Transfer(ambulance.departureTime, ambulance.patient.severity));
			}
		}
	}

	private class ExitSimulationException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	}

	private class ResetSimulationException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	}

	private void initializationRoutine() {
		deaths = 0;
		transfers = new ArrayList<Transfer>();
		systemState = new SystemState();
		statisticalCounters = new StatisticalCounters();
		timeProfile = new TimeProfile();
	}

	private int timingRoutine() {
		return timeProfile.updateClocks();
	}

	private void arrivalEventRoutine() throws ResetSimulationException, ExitSimulationException {
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

	private void departureEventRoutine() throws ResetSimulationException, ExitSimulationException {
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

	private double uniformLibraryRoutine() {
		return Math.random();
	}

	private double exponentialLibraryRoutine() {
		return Math.log(1 - Math.random()) / (-rateParameter);
	}

	private void calculateCounters() {
		avgNumAmbulance += numAmbulance;
		avgDelay += statisticalCounters.totalDelay / statisticalCounters.numDelay;
		avgQueueLength += statisticalCounters.areaQt / timeProfile.currClock;
		avgPercUtilization += (statisticalCounters.areaBt / timeProfile.currClock) * 100;
	}

	private void reportGenerator() {
		avgNumAmbulance /= numSimulation;
		avgDelay /= numSimulation;
		avgQueueLength /= numSimulation;
		avgPercUtilization /= numSimulation;
	}

	private void mainProgram() {
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
			// call channel2 with transfers
		}
		reportGenerator();
	}

	public double getAvgNumAmbulance() {
		return avgNumAmbulance;
	}

	public double getAvgDelay() {
		return avgDelay;
	}

	public double getAvgQueueLength() {
		return avgQueueLength;
	}

	public double getAvgPercUtilization() {
		return avgPercUtilization;
	}

	public void print() {
		System.out.printf("Number of Ambulances:\t%.4f\n", avgNumAmbulance);
		System.out.printf("Average Delay:\t\t%.4f\n", avgDelay);
		System.out.printf("Average Queue Length:\t%.4f\n", avgQueueLength);
		System.out.printf("Percentage Utilization:\t%.4f\n", avgPercUtilization);
	}

}
