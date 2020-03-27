package noodleBot.robot;

import battlecode.common.*;

public class FulfillmentCenter extends AbstractRobotPlayer {
    Direction spawnDir;

    public FulfillmentCenter(RobotController rc) throws GameActionException {
        super(rc);
        currentLocation = rc.getLocation();
        findHQ();
        System.out.println(hqLocation);
        spawnDir = updateSpawn();
    }

    @Override
    protected void run() throws GameActionException {
        decodeBlock(rc.getRoundNum() - 1);
        System.out.println(spawnDir);
        System.out.println(occupiedPlaces);
        if (damBuildingPhase) {
            if (spawnDir != null && !occupiedPlaces.contains(spawnDir) && !rc.isLocationOccupied(currentLocation.add(spawnDir))) {
                tryBuild(RobotType.DELIVERY_DRONE, spawnDir);
            } else {
                if (!(rc.canSenseLocation(currentLocation.add(spawnDir)) &&
                        rc.senseRobotAtLocation(currentLocation.add(spawnDir)) != null &&
                        rc.senseRobotAtLocation(currentLocation.add(spawnDir)).getType() == RobotType.DELIVERY_DRONE)) {
                    System.out.println("Do i get here");
                    spawnDir = updateSpawn();
                }
            }
        } else {
            scanEnemy();
        }
    }

    /**
     * Checks if the current spawn direction is still valid, updates it if it is not valid anymore.
     * @return
     * @throws GameActionException
     */
    protected Direction updateSpawn() throws GameActionException {
        for (Direction dir : Direction.cardinalDirections()) {
            MapLocation checkLoc = currentLocation.add(dir);
            System.out.println("Direction: " + dir);
            System.out.println("reserved: " + !occupiedPlaces.contains(checkLoc));
            System.out.println("dam: " + !damSet.contains(checkLoc));
            System.out.println("adjacent: " + hqLocation.isAdjacentTo(checkLoc));
            System.out.println("sensable: " + rc.canSenseLocation(checkLoc));
            System.out.println("occupied: " + (rc.senseRobotAtLocation(checkLoc) == null));
            if (rc.senseRobotAtLocation(checkLoc) != null) {
                System.out.println("by drone: " + (rc.senseRobotAtLocation(checkLoc).getType() == RobotType.DELIVERY_DRONE));
            }
            if (!occupiedPlaces.contains(checkLoc) && !damSet.contains(checkLoc) &&
                    hqLocation.isAdjacentTo(checkLoc) && rc.canSenseLocation(checkLoc) &&
                    ((rc.senseRobotAtLocation(checkLoc) == null) || (rc.senseRobotAtLocation(checkLoc).getType() == RobotType.DELIVERY_DRONE))) {
                return currentLocation.directionTo(checkLoc);
            }
        }

        for (Direction dir : Direction.cardinalDirections()) {
            MapLocation checkLoc = currentLocation.add(dir.rotateRight());
            if (!occupiedPlaces.contains(checkLoc) && !damSet.contains(checkLoc) &&
                    hqLocation.isAdjacentTo(checkLoc) && rc.canSenseLocation(checkLoc) &&
                    (rc.senseRobotAtLocation(checkLoc) == null || rc.senseRobotAtLocation(checkLoc).getType() == RobotType.DELIVERY_DRONE)) {
                return currentLocation.directionTo(checkLoc);
            }
        }
        return null;
    }

    /**
     * Scans for enemy units in its vision radius, returns true if found
     * @return
     * @throws GameActionException
     */
    protected boolean scanEnemy() throws GameActionException {
        for (RobotInfo robot : findNearbyEnemies()) {
            if (robot.getType().canBePickedUp()) {
                if (tryBuild(RobotType.DELIVERY_DRONE, currentLocation.directionTo(robot.location))) {
                    return true;
                } else {
                    for (Direction dir : Direction.allDirections()) {
                        tryBuild(RobotType.DELIVERY_DRONE, dir);
                    }
                }
            }
        }
        return false;
    }
}
