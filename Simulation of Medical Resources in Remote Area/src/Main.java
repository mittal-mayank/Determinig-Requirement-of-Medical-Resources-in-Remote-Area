
public class Main {
	public static void main(String[] args) {
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
}
