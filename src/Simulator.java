import java.util.concurrent.Semaphore;

/**
 * Do people who forget to lock the bathroom door have a higher chance of being 
 * walked in on if the door has "vacant" or "occupied" written on it?
 * 
 * Assumptions (A is in the bathroom, and B is outside):
 * 1. If B knocks while A is inside, A will always say, "Occupied!" so B will
 * never walk in on him.
 * 2. If the door reads "occupied", B will never knock.
 * 3. If the door reads "vacant" or if there are no indicators on the door,
 * B may or may not knock. The chances of B's knocking if the door says 
 * vacant are lower than the chances of his knocking if the door doesn't say
 * anything at all.
 * 
 * @author abhishekchatterjee
 */
public class Simulator {
	// Constants
	private static int TIME_IN_BATHROOM = 200; // milliseconds
	private static double PROBABILITY_OF_LOCKING_DOOR = 0.8;
	private static double PROBABILITY_OF_KNOCKING_1 = 0.7;
	private static double PROBABILITY_OF_KNOCKING_2 = 0.2;
	private static double PROBABILITY_OF_HAVING_TO_USE_BATHROOM = 0.5;
	private static int RUN_TIME = 10; // seconds
	
	private static Semaphore bathroom;
	private static boolean[] people = new boolean[10];
	private static boolean doorMarkings = false;
	private static boolean locked = false;
	
	private static class Person extends Thread {
		private final int id;
		private int conflicts; 
		private int lastI = 0;
		private boolean hasToUse = false;
		private long lastTime = 0;

		public Person(int id) {
			this.id = id;
			conflicts = 0;
		}
		
		public int getConflicts() {
			return conflicts;
		}
		
		private void print(String s) {
			System.out.println(id + ": " + s);
		}
		
		private boolean willUseBathroom() {
			if (hasToUse) {
				return true;
			}
			
			long elapsed = System.currentTimeMillis() - lastTime;
			
			return Math.random() <= (PROBABILITY_OF_HAVING_TO_USE_BATHROOM*elapsed)/1000.0/RUN_TIME;
		}
		
		private boolean willKnock() {
			double probability = doorMarkings ? PROBABILITY_OF_KNOCKING_2 : PROBABILITY_OF_KNOCKING_1;
			return Math.random() <= probability;
		}
		
		private void useBathroom() throws InterruptedException {
			if (doorMarkings && locked) {
				print("Door says occupied, so I don't try.");
				return;
			}
			
			// If I knock and the bathroom is occupied, I back off.
			if (willKnock() && bathroomInUse()) {
				print("I knocked, but the bathroom was in use.");
				return;
			} 
			
			boolean tried = bathroom.tryAcquire();
			
			if (tried) {
				print("Acquired!");
				locked = Math.random() <= PROBABILITY_OF_LOCKING_DOOR;
				Thread.sleep(TIME_IN_BATHROOM);
				locked = false;
				bathroom.release();
				hasToUse = false;
				lastTime = System.currentTimeMillis();
				print("And out!");
			} else {
				if (!locked) {
					print("I went in, but someone was already inside!");
					conflicts++;
				} else {
					print("I tried the door, but it was locked");
				}
			}
		}
		
		private boolean bathroomInUse() {
			return bathroom.availablePermits() < 1;
		}
		
		public void run() {
			long startTime = System.currentTimeMillis();
			try {
				do {
					if (willUseBathroom()) {
						print(id + " wants to use the bathroom");
						useBathroom();
					}
				} while (System.currentTimeMillis() - startTime <= RUN_TIME*1000);
				print("All done");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static int runSimulation(boolean doorSigns) {
		bathroom = new Semaphore(1);
		doorMarkings = doorSigns;
		Person[] persons = new Person[10];
		for (int i=0; i<10; i++) {
			persons[i] = new Person(i);
		}
		
		System.out.println("Begin experiment");
		for (Person person : persons) {
			person.start();
		}
		
		for (Person person : persons) {
			try {
				person.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		int conflicts = 0;
		for (Person person : persons) {
			conflicts += person.getConflicts();
		}
		
		System.out.println("End experiment");
		
		return conflicts;
	}
	
	/**
	 * Runs a simulation
	 * @param doorMarkers true if door indicates "vacant" or "occupied".
	 */
	public static void main(String[] args) {
		int conflicts1 = runSimulation(false);
		int conflicts2 = runSimulation(true);
		
		System.out.println("Number of times people walked into an occupied bathroom:");
		System.out.println("In experiment 1: " + conflicts1);
		System.out.println("In experiment 2: " + conflicts2);
	}
}
