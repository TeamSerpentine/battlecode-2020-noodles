package noodleBot.robot;

import battlecode.common.*;

import java.util.*;

public class DeliveryDrone extends AbstractRobotPlayer {
    MapLocation[] possibleEnemyHQ;
    Queue<MapLocation> nextPossibleEnemyHQ = new LinkedList<>();
    MapLocation enemyHQ;
    MapLocation spawnLocation;
    boolean holdingFriendly = false;
    boolean certain = false;
    boolean escaping = false;
    boolean movedOut = false;

    public DeliveryDrone(RobotController rc) throws GameActionException {
        super(rc);
        spawnLocation = rc.getLocation();
        System.out.println("I Spawned: " + spawnLocation);
        scanFriendlyHQ();
        System.out.println("My home: " + hqLocation);
        if (hqLocation != null) {
            possibleEnemyHQ = possibleOpposites(hqLocation);
        } else {
            possibleEnemyHQ = possibleOpposites(spawnLocation);
        }
        determineDam();

        for (MapLocation loc : possibleEnemyHQ) {
            nextPossibleEnemyHQ.add(loc);
        }

        System.out.println("I am going to raid these Locations" + possibleEnemyHQ[0]);
    }

    @Override
    protected void run() throws GameActionException {
        currentLocation = rc.getLocation();
        decodeBlock(rc.getRoundNum() - 1);
        if (damSetDetermined) {
            if (tryPickupEnemy()) {
                escaping = true;
            }
            if (movedOut) {
                beAggressive();
            } else {
                if (!escaping) {
                    escaping = bePassive();
                } else {
                    movedOut = tryMoveOut();
                }
            }
        } else {
            damSetDetermined = determineDam();
        }
    }

    /**
     * Drone moves landscapers to the dam until it is full
     *
     * @return false - if it can no longer add drones to the dam
     * @throws GameActionException
     */
    protected boolean bePassive() throws GameActionException {
        return tryFillDam();
    }


    /**
     * Drone actively seeks for enemies to kill
     *
     * @throws GameActionException
     */
    protected void beAggressive() throws GameActionException {
        System.out.println("Attaaaaaaaack");
        if (!rc.isCurrentlyHoldingUnit()) {
            System.out.println("trying to pick up enemy unit");
            tryPickupEnemy();
        } else {
            tryKill();
        }
        if (enemyHQ == null) {
            tryMoveToEnemy();
        } else {
            if (enoughDrones()) {
                attackMove();
            }
        }
        tryMoveToEnemy();
    }

    /**
     * Checks if there are more then the MINIMUM_DRONES_FOR_ATTACK in this drones vision radius or
     * if there is a friendly drone attacking
     * @return
     */
    protected boolean enoughDrones() {
        int allies = 0;
        for (RobotInfo robot : findNearbyFriendlies()) {
            if (robot.getType() == RobotType.DELIVERY_DRONE) {
                allies++;
            }
            if (enemyHQ.isWithinDistanceSquared(robot.location, GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED)) {
                return true;
            }
        }
        return (allies >= MINIMUM_DRONES_FOR_ATTACK);
    }

    /**
     * Moves to the location of the enemy HQ
     * @throws GameActionException
     */
    protected void attackMove() throws GameActionException {
        if (rc.canMove(currentLocation.directionTo(enemyHQ))) {
            rc.move(currentLocation.directionTo(enemyHQ));
        }

    }

    /**
     * if the enemyHQ is known flies towards the enemy hq, otherwise it will explore the possible locations
     *
     * @return false
     * @throws GameActionException
     */
    protected boolean tryMoveToEnemy() throws GameActionException {
        if (enemyHQ != null) {
            return tryMoveToLocationDrone(enemyHQ);
        } else {
            explore();
            return false;
        }
    }

    /**
     * Explores the possible locations of the enemy hq
     *
     * @throws GameActionException
     */
    protected void explore() throws GameActionException {
        if (rc.getLocation().equals(nextPossibleEnemyHQ.peek())) {
            nextPossibleEnemyHQ.remove();
        }
        tryMoveToLocationDrone(nextPossibleEnemyHQ.peek());
        scanEnemy();
    }

    /**
     * Tries to kill the unit it is currently holding
     * @return
     * @throws GameActionException
     */
    protected boolean tryKill() throws GameActionException {
        if (tryDropInWater()) {
            return true;
        } else {
            for (Direction dir : Direction.allDirections()) {
                MapLocation loc = currentLocation.add(dir);
                if (rc.canSenseLocation(loc) && rc.senseFlooding(loc)) {
                    tryMoveDrone(dir);
                }
            }
            if (enemyHQ != null && rc.canSenseLocation(enemyHQ)) {
                tryMoveDrone(enemyHQ.directionTo(currentLocation));
            } else {
                tryMoveDrone(randomDirection());
            }
            return false;
        }
    }


    /**
     * tries to fill the dam
     *
     * @return when the dam is filled it returns true
     * @throws GameActionException
     */
    protected boolean tryFillDam() throws GameActionException {
        if (!isDamFull()) {
            tryMoveLandscaper();
            System.out.println("Trying to fill the dam");
            return isDamFull();
        }
        System.out.println("The dam has been filled");
        return true;
    }

    /**
     * tries to move the drone out of the dike
     *
     * @return true if successful false otherwise
     * @throws GameActionException
     */
    protected boolean tryMoveOut() throws GameActionException {
        MapLocation ownLocation = rc.getLocation();
        // already has unit, now needs to move out
        if (rc.isCurrentlyHoldingUnit()) {
            System.out.println("I have hold of a unit");
            if (hqLocation.isAdjacentTo(ownLocation)) {
                System.out.println("I am inside the dam");
                for (MapLocation dam : damSet) {
                    if (dam.isAdjacentTo(ownLocation) && rc.canSenseLocation(dam) && !rc.isLocationOccupied(dam)) {
                        tryMoveToLocationDrone(dam);
                    }
                }
                return false;
            } else if (locatedOnDam(ownLocation)) {
                System.out.println("I am on the dam");
                tryMoveDrone(hqLocation.directionTo(ownLocation));
                return false;
            } else if (isAdjacentToDam(ownLocation)) {
                System.out.println("I think I have escaped, placing landscaper back");
                return tryDropOnDam();
            } else {
                System.out.println("I have a landscaper but am not adjacent to my dam");
                //Move towards the hq as that should be in the direction of the dam
                tryMoveDrone(ownLocation.directionTo(hqLocation));
                return false;
            }
        } else if (locatedOnDam(ownLocation)) {
            // is on the dam so now can move out;
            return tryMoveDrone(hqLocation.directionTo(ownLocation));
        } else if (hqLocation.isAdjacentTo(ownLocation)) {
            if (isDamFull() && rc.getTeamSoup() > 300) {
                // Does not have a unit and there is no (visible) free spot to move out
                // so pick up unit
                System.out.println("I dont have a open spot yet");
                RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, rc.getTeam());
                for (RobotInfo robot : robots) {
                    System.out.println("Checking" + robot.getID());
                    if (robot.getType().canBePickedUp() && locatedOnDam(robot.getLocation()) && rc.canPickUpUnit(robot.getID())) {
                        System.out.println("Picking up" + robot.getID());
                        rc.pickUpUnit(robot.getID());
                        return false;
                    }
                }
            } else {
                // the dam has a free spot but the drone has not moved out yet
                tryMoveLandscaper();
            }
            return false;
        } else {
            // not on dam and not next to hq, should be clear to go and attack
            System.out.println("We have reached impossible Location 146 Delivery Drone");
            return false;
        }
    }


    /**
     * scans for the enemy HQ
     *
     * @return true if it found a enemyHQ false otherwise
     */
    protected void scanEnemy() throws GameActionException {
        for (RobotInfo robot : findNearbyEnemies()) {
            if (!certain && robot.getType() == RobotType.HQ) {
                enemyHQ = robot.location;
                enemyNG.add(enemyHQ);
                System.out.println("Found Enemy HQ");
                pointOfInterest eHQ = new pointOfInterest(ENEMY_HQ_CODE, robot.location, -1);
                trySendMessage(eHQ);
            }
            if (robot.getType() == RobotType.NET_GUN) {
                pointOfInterest enemyNG = new pointOfInterest(ENEMY_NET_GUN_CODE, robot.location, -1);
                trySendMessage(enemyNG);
            }
        }
    }


    /**
     * scans for the friendly HQ
     */
    protected void scanFriendlyHQ() {
        if (hqLocation == null) {
            for (RobotInfo robot : findNearbyFriendlies()) {
                if (robot.getType() == RobotType.HQ) {
                    hqLocation = robot.location;
                }
            }
        }
    }

    /**
     * Checks if there is an enemy and picks it up if one is found
     *
     * @return true - if it was succesful
     * @throws GameActionException
     */
    protected boolean tryPickupEnemy() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
        RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);
        for (RobotInfo robot : robots) {
            if (rc.canPickUpUnit(robot.getID())) {
                rc.pickUpUnit(robot.getID());
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to pickup a friendly if possible
     *
     * @return true - if succesful
     * @throws GameActionException
     */
    protected boolean tryPickupFriendly() throws GameActionException {
        Team enemy = rc.getTeam();
        // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
        RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);
        for (RobotInfo robot : robots) {
            if (rc.canPickUpUnit(robot.getID())) {
                rc.pickUpUnit(robot.getID());
                holdingFriendly = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to move a drone towards direction, does not check for flooding
     *
     * @param dir Direction to move towards
     * @return true - if succesful
     * @throws GameActionException
     */
    private boolean tryMoveDrone(Direction dir) throws GameActionException {
        System.out.println("Try moving " + dir);
        System.out.println(saveMove(dir));
        if (rc.canMove(dir) && saveMove(dir)) {
            rc.move(dir);
            return true;
        }
        for (int i = 0; i < 7; i++) {
            if (turnside)
                dir = dir.rotateRight();
            else
                dir = dir.rotateLeft();
            if (rc.canMove(dir) && saveMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    private boolean saveMove(Direction dir) throws GameActionException {
        MapLocation newLoc = currentLocation.add(dir);
        for (MapLocation netGun : enemyNG) {
            if (netGun.isWithinDistanceSquared(newLoc, GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves a drone in the direction of the given location
     *
     * @param loc Target location
     * @return true - if step was succesful
     * @throws GameActionException
     */
    protected boolean tryMoveToLocationDrone(MapLocation loc) throws GameActionException {
        Direction dir = rc.getLocation().directionTo(loc);
        return tryMoveDrone(dir);
    }

    /**
     * Tries to drop the unit which this drone is holding in to the water
     * Checks if there is water and drops the unit it is holding in to that
     *
     * @return true - if successful
     * @throws GameActionException
     */
    protected boolean tryDropInWater() throws GameActionException {
        for (Direction dir : Direction.allDirections()) {
            MapLocation dropLoc = rc.getLocation().add(dir);
            if (rc.canDropUnit(dir) &&
                    rc.canSenseLocation(dropLoc) &&
                    rc.senseFlooding(dropLoc)) {
                rc.dropUnit(dir);
                return true;
            }
        }
        return false;
    }

    /**
     * tries to move a landscaper on to the dam
     *
     * @return true if successful
     * @throws GameActionException
     */
    protected boolean tryMoveLandscaper() throws GameActionException {
        if (rc.isCurrentlyHoldingUnit()) {
            System.out.println("Moving a landscaper to the dam");
            return tryDropOnDam();
        } else {
            System.out.println("is the dam full?" + isDamFull());
            System.out.println("Trying to pick someone up");
            RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, rc.getTeam());
            for (RobotInfo robot : robots) {
                System.out.println();
                if ((robot.getType() == RobotType.LANDSCAPER)
                        && rc.canPickUpUnit(robot.getID())
                        && !locatedOnDam(robot.getLocation())) {
                    rc.pickUpUnit(robot.getID());
                    holdingFriendly = true;
                }
            }
        }
        return false;
    }

    /**
     * tries to drop the unit it is holding on the dam
     *
     * @return true if successful false otherwise
     * @throws GameActionException
     */
    protected boolean tryDropOnDam() throws GameActionException {
        for (MapLocation dike : damSet) {
            // check if within drop radius
            if (dike.isWithinDistanceSquared(rc.getLocation(), GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED)) {
                if (rc.canDropUnit(rc.getLocation().directionTo(dike))
                        && !isEnemyDroneNear(currentLocation.add(currentLocation.directionTo(dike)))) {
                    //drop unit
                    rc.dropUnit(rc.getLocation().directionTo(dike));
                    return true;
                }
            }
        }
        if (currentLocation.equals(spawnLocation)) {
            return false;
        } else {
            return tryMoveDrone(currentLocation.directionTo(lowestDamLoc()));
        }
    }

    /**
     * Checks if the drone is adjacent to a damLocation
     * @param loc
     * @return
     */
    protected boolean isAdjacentToDam(MapLocation loc) {
        for (MapLocation dam : damSet) {
            if (loc == dam) {
                return false;
            } else if (loc.isAdjacentTo(dam)) {
                return true;
            }
        }
        return false;
    }

    /**
     * check if there is a enemy drone next to the tile that the LS could move to
     *
     * @return true - if there is a drone
     * @throws GameActionException
     */
    public boolean isEnemyDroneNear(MapLocation m) {
        for (RobotInfo r : rc.senseNearbyRobots(m, 3, team.opponent())) {
            if (r.getType().equals(RobotType.DELIVERY_DRONE)) return true;
        }
        return false;
    }
}
