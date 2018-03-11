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
	private static final int TIME_IN_BATHROOM = 200; // milliseconds
	private static final double PROBABILITY_OF_LOCKING_DOOR = 0.8;
	private static final double PROBABILITY_OF_KNOCKING_1 = 0.7; // If the door doesn't indicate occupied/vacant, there is a higher probability
	private static final double PROBABILITY_OF_KNOCKING_2 = 0.1; // that a person will knock than if it doesn't.
	private static final double BASE_PROBABILITY_OF_HAVING_TO_USE_BATHROOM = 0.2; // This will be weighted by time since a person last used the bathroom.
	private static final int RUN_TIME = 10000; // milliseconds
	private static final int BACKOFF = 900; // milliseconds; back off if you fail to acquire bathroom.
	
	// Static variables
	private static Bathroom bathroom;
	private static boolean doorMarkings = false;
	
	private static class Bathroom extends Semaphore {
		private boolean locked = false;

		public Bathroom() {
			super(1);
		}
		
		public boolean isLocked() {
			return locked;
		}
		
		public boolean tryLock() {
			boolean tried = this.tryAcquire();
			if (tried) {
				this.locked = Math.random() <= PROBABILITY_OF_LOCKING_DOOR;
			}
			return tried;
		}
		
		public void releaseLock() {
			this.release();
			this.locked = false;
		}
		
		public boolean isAvailable() {
			return this.availablePermits() > 0;
		}
	}
	
	private static class Person extends Thread {
		private final int id;
		private int conflicts; 
		private int uses;
		private boolean hasToUse = false;
		private long lastTime = 0;
		private long startTime = 0;

		public Person(int id) {
			this.id = id;
			conflicts = 0;
			uses = 0;
		}
		
		public int getConflicts() {
			return conflicts;
		}
		
		private int getUses() {
			return uses;
		}
		
		private void print(String s) {
			double timestamp = ((System.currentTimeMillis() - startTime)/10)/100.0;
			System.out.println(id + ": " + s + " (" + timestamp + ")");
		}
		
		private boolean willUseBathroom() {
			// A person who has to use the bathroom but hasn't yet will not 
			// not have to use the bathroom anymore just because you asked him again.
			if (hasToUse) {
				return true;
			}
			
			long elapsed = System.currentTimeMillis() - lastTime;
			if (elapsed < 2000) {
				return false;
			}
			
			return Math.random() <= (BASE_PROBABILITY_OF_HAVING_TO_USE_BATHROOM*elapsed)/(1.0*RUN_TIME);
		}
		
		private boolean willKnock() {
			double probability = doorMarkings ? PROBABILITY_OF_KNOCKING_2 : PROBABILITY_OF_KNOCKING_1;
			return Math.random() <= probability;
		}
		
		private void useBathroom() throws InterruptedException {
			if (doorMarkings && bathroom.isLocked()) {
				print("Door says occupied, so I don't try.");
				Thread.sleep(BACKOFF);
				return;
			}
			
			// If I knock and the bathroom is occupied, I back off.
			if (willKnock() && !bathroom.isAvailable()) {
				print("I knocked, but the bathroom was in use.");
				Thread.sleep(BACKOFF);
				return;
			} 
			
			boolean tried = bathroom.tryLock();
			
			if (tried) {
				if (bathroom.isLocked()) {
					print("Acquired and locked the door.");
				} else {
					print("Acquired but forgot to lock.");
				}
				uses++;
				Thread.sleep(TIME_IN_BATHROOM);
				bathroom.releaseLock();
				hasToUse = false;
				lastTime = System.currentTimeMillis();
				print("And out!");
			} else {
				if (!bathroom.isLocked()) {
					print("I went in, but someone was already inside!");
					conflicts++;
				} else {
					print("I tried the door, but it was locked");
				}
				// Back off after failed attempt to acquire bathroom. This is to give the current occupant of the bathroom
				// the privacy he deserves.
				Thread.sleep(BACKOFF);
			}
		}
		
		public void run() {
			startTime = System.currentTimeMillis();
			try {
				do {
					if (willUseBathroom()) {
						print("Wants to use the bathroom");
						useBathroom();
					}
				} while (System.currentTimeMillis() - startTime <= RUN_TIME);
				print("All done");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static class ResultPair {
		int a;
		int b;
		
		public ResultPair(int a, int b) {
			this.a = a;
			this.b = b;
		}
	}
	
	private static ResultPair runSimulation(boolean doorSigns) {
		bathroom = new Bathroom();
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
		int uses = 0;
		for (Person person : persons) {
			conflicts += person.getConflicts();
			uses += person.getUses();
		}
		
		System.out.println("End experiment");
		
		return new ResultPair(conflicts, uses);
	}
	
	/**
	 * Runs a simulation
	 * @param doorMarkers true if door indicates "vacant" or "occupied".
	 */
	public static void main(String[] args) {
		ResultPair result1 = runSimulation(false);
		ResultPair result2 = runSimulation(true);
		
		System.out.println("Number of times people walked into an occupied bathroom:");
		System.out.println("In experiment 1: " + result1.a + " / " + result1.b + " uses of the bathroom");
		System.out.println("In experiment 2: " + result2.a + " / " + result2.b + " uses of the bathroom");
	}
}
