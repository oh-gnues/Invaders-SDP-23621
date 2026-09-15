# GoG

## Team Introduction

Team members contain of student from Malaysia. We mainly focus on developing something that can give a big impact on currency system. It will not be too fancy since our focus is code reliability, stability and reusable rather than a fancy but once-used code.

## Members

| Name | Role | GitHub |
| --- | --- | --- |
| Haikal fiqri | Team Leader | [haikalfiqri](https://github.com/haikalfiqri) |
| Ikhwan Zarif | Collaborator | [wamos0922](https://github.com/wamos0922)|
| Nik Ammar | Code Checker | [nikmar9705](https://github.com/nikmar9705) |
| Zarith Imran | Code Builder | [zeth-ux](https://github.com/zeth-ux) |
| Wafi | Presentor | [wafisapuan](https://github.com/wafisapuan) |
| Muizuddin | Bug Tester | [mfuizz](https://github.com/mfuizz) |
| Aqil Amani | Bug Solver | [Soy05](https://github.com/Soy05) |
| Ashrafuddin Fadhil | Database | [asyrafuddin](https://github.com/asyrafuddin) |
| Akif | UI Designer | [abangBoy13](https://github.com/abangBoy13) |

## Responsibilities

# 💰 Currency System

The **Currency System** manages the player's money and rewards throughout the game.
Players can earn currency by defeating enemies, completing challenges, and progressing through levels. The earned currency can then be used to purchase items from the **Item System**.

The system also supports **dual currencies**, similar to games such as *Mobile Legends*, where players have a regular currency and a premium currency.

---

## 🎯 System Goals

The main goals of the Currency System are:

* Reward players for defeating enemies.
* Allow players to check their current currency balance.
* Allow players to spend currency on items.
* Provide additional rewards for completing challenges.
* Give bonus currency when reaching certain levels.
* Support two different types of currency.
* Increase rewards based on game difficulty.
* Connect the currency information with the Gameplay HUD.

---

# 💵 Currency Types

The system uses **two types of currency**.

### 1. 🪙 Coins

Coins are the main currency that players earn during normal gameplay.

Players can obtain coins by:

* Defeating minions.
* Completing challenges.
* Reaching certain levels.
* Playing on higher difficulties.

Coins are mainly used to purchase normal items.

### 2. 💎 Diamonds

Diamonds are the second/premium currency.

They can be used for special or more valuable items depending on the design of the **Item System**.

> The exact way diamonds are obtained and spent can be adjusted depending on the final game design.

---

# 🏆 How Players Earn Currency

## 1. Defeating Minions

Players receive coins when they defeat enemies.

Example:

```text
Defeat Minion
      ↓
Calculate Reward
      ↓
Add Coins to Player Balance
      ↓
Update HUD
```

The amount of money earned can depend on the difficulty or type of enemy.

Example:

| Enemy Difficulty | Coin Reward |
| ---------------- | ----------: |
| Easy Minion      |   +10 Coins |
| Normal Minion    |   +20 Coins |
| Hard Minion      |   +35 Coins |
| Elite Minion     |   +50 Coins |

---

## 2. Challenge Rewards

Players can receive additional currency by completing specific challenges.

Some challenges may provide a **double-money reward**.

Example:

```text
Complete Challenge
        ↓
Check Challenge Reward
        ↓
Apply Reward Multiplier
        ↓
Add Currency
```

Example:

> Normal reward: **100 Coins**
> Challenge multiplier: **×2**
> Final reward: **200 Coins**

The challenge system should communicate with the Currency System when a challenge has been successfully completed.

---

## 3. Level Completion Rewards

Players receive extra currency when successfully passing a certain level.

Example:

| Level   |      Bonus |
| ------- | ---------: |
| Level 1 |  +50 Coins |
| Level 2 |  +75 Coins |
| Level 3 | +100 Coins |
| Level 4 | +150 Coins |

This encourages players to continue progressing through the game.

---

# 🔥 Difficulty-Based Rewards

Higher difficulty levels provide higher currency rewards.

The system can use a reward multiplier based on the selected difficulty.

Example:

| Difficulty | Reward Multiplier |
| ---------- | ----------------: |
| Easy       |              ×1.0 |
| Normal     |              ×1.5 |
| Hard       |              ×2.0 |
| Extreme    |              ×3.0 |

### Example

If a minion normally gives **20 Coins**:

```text
Easy:
20 × 1.0 = 20 Coins

Normal:
20 × 1.5 = 30 Coins

Hard:
20 × 2.0 = 40 Coins

Extreme:
20 × 3.0 = 60 Coins
```

This creates a risk-and-reward system where players can earn more money by playing at higher difficulty.

---

# 💰 Currency Balance

The Currency System keeps track of the player's current balance.

Example:

```text
Coins:    1,250
Diamonds: 50
```

The balance should be updated whenever the player:

* Earns currency.
* Spends currency.
* Completes a challenge.
* Defeats an enemy.
* Completes a level.

The system should prevent the player's balance from becoming negative.

---

# 🛒 Purchasing Items

The Currency System works together with the **Item System** to handle purchases.

The Item System determines the item's price, while the Currency System checks whether the player has enough currency.

---

# 🖥️ Gameplay HUD Integration

The Currency System is connected to the **Gameplay HUD System**.

The HUD should display the player's current currency balance during gameplay.

