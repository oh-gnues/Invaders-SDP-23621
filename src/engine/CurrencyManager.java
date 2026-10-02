package engine;

/**
 * Minimal, in-memory placeholder for the team's overall currency system.
 *
 * This only tracks coins collected during the current session, which is
 * enough to demo and test the coin-drop feature end-to-end. It is
 * intentionally small: persistence, diamonds, spending and balance display
 * belong to the rest of the Currency System requirements (see
 * teams/GoG.md) and should replace or wrap this class once those pieces
 * are merged. Keeping this separate from GameScreen's local bookkeeping
 * gives the HUD/shop teams a single, obvious place to read the balance
 * from.
 *
 * @author GoG - Currency System
 */
public final class CurrencyManager {

	/** Singleton instance. */
	private static CurrencyManager instance;

	/** Coins collected so far in this session. */
	private int coins;

	/**
	 * Private constructor.
	 */
	private CurrencyManager() {
		this.coins = 0;
	}

	/**
	 * Controls access to the currency manager.
	 *
	 * @return shared instance of CurrencyManager.
	 */
	public static CurrencyManager getInstance() {
		if (instance == null)
			instance = new CurrencyManager();
		return instance;
	}

	/**
	 * Adds coins to the current balance, e.g. when the player collects a
	 * dropped coin.
	 *
	 * @param amount
	 *            Amount of coins to add. Ignored if not positive.
	 */
	public void addCoins(final int amount) {
		if (amount > 0)
			this.coins += amount;
	}

	/**
	 * @return current coin balance.
	 */
	public int getCoins() {
		return this.coins;
	}

	/**
	 * Attempts to spend coins, e.g. for a shop purchase or ship unlock.
	 * Deliberately checks-then-spends atomically so a caller never needs to
	 * call getCoins() first and race against another deduction.
	 *
	 * @param amount
	 *            Amount of coins to spend. Must be positive.
	 * @return true if the balance had enough coins and the amount was
	 *         deducted; false if funds were insufficient and nothing
	 *         changed.
	 */
	public boolean trySpend(final int amount) {
		if (amount <= 0)
			throw new IllegalArgumentException("amount must be positive");
		if (this.coins < amount)
			return false;
		this.coins -= amount;
		return true;
	}

	/**
	 * Resets the balance to zero. Useful for tests and new sessions until
	 * a real persistence layer is in place.
	 */
	public void reset() {
		this.coins = 0;
	}
}
