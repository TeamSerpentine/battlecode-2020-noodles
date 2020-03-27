package noodleBot.robot;

import battlecode.common.*;

import java.util.ArrayList;


public class Miner extends AbstractRobotPlayer {
    public MapLocation senseLocation;
    public ArrayList<MapLocation> reservedLocations = new ArrayList<>();
    public int roundsWithoutRefinery = 0;

    public Miner(RobotController rc) {
        super(rc);
        initLocations();
    }

    @Override
    protected void run() throws GameActionException {
        currentLocation = rc.getLocation();
        decodeBlock(rc.getRoundNum() - 1);
        updateFromVision();
        if (rc.isReady()) {
            if (!damSetDetermined) {
                damSetDetermined = determineDam();
            }
            if (damBuildingPhase || rc.getRoundNum() > START_DAM_BUILDING_ROUND) {
                evacuateHQ();
            }
            if (hasTasks()) {
                doTasks();
            } else if (!tryResourceHunt()) {
                ecoBoost();
                scout();

            }
        }
        droneDefense();
    }

    /**
     * checks whether the miner has tasks
     *
     * @return true miner has tasks
     * @throws GameActionException
     */
    public boolean hasTasks() {
        return !unitTask.isEmpty();
    }

    /**
     * try to move away from HQ if possible or suicide
     *
     * @throws GameActionException
     */
    public void evacuateHQ() throws GameActionException {
        if (insideDam(currentLocation) || damSet.contains(currentLocation)) {
            System.out.println("Suicide");
            if (rc.isReady() && !tryMoveExact(hqLocation.directionTo(currentLocation))) {
                tryRefine(currentLocation.directionTo(hqLocation));
                rc.disintegrate();
            }
        }
    }

    /**
     * try to build a building that is needed for the dam
     *
     * @throws GameActionException
     */
    public void doTasks() throws GameActionException {
        if (!unitTask.isEmpty()) {
            System.out.println("I have a task!");
            for (pointOfInterest task : unitTask) {
                System.out.println(task);
                if (tryBuildPoint(task)) {
                    System.out.println(codeToType(task.buildCode));
                    unitTask.remove(task);
                }
            }
        }
    }

    public boolean tryResourceHunt() throws GameActionException {
        if (rc.getSoupCarrying() >= RobotType.MINER.soupLimit - GameConstants.SOUP_MINING_RATE) {
            goToRefinery();
            System.out.println("Moving to refinery");
            System.out.println("I have: " + rc.getSoupCarrying() + " soup");
            return true;
        } else if (nextToSoup()) {
            mineSoup();
            System.out.println("Mining soup");
            return true;
        } else {
            MapLocation soup = senseClosestSoup();
            if (soup != null) {
                moveToLocation(soup);
                System.out.println("Moving to soup");
                return true;
            }
            return false;
        }
    }

    /**
     * try to build vaporator if there is enough soup to spare
     *
     * @throws GameActionException
     */
    public void ecoBoost() throws GameActionException {
        if (rc.getRoundNum() < 400 && (damBuildingPhase || rc.getTeamSoup() >= RobotType.VAPORATOR.cost + RESERVE_SOUP)) {
            tryBuildHighest(RobotType.VAPORATOR);
        }
    }

    /**
     * try to move in a direction that is unpopulated for more chance to find soup
     *
     * @throws GameActionException
     */
    public void scout() throws GameActionException {
        System.out.println("Scouting the area");
        if (hqLocation.distanceSquaredTo(currentLocation) > 6) {
            RobotInfo[] robots = rc.senseNearbyRobots();
            System.out.println(robots);
            boolean sawOthers = false;
            for (RobotInfo robot : robots) {
                System.out.println(robot.getLocation());
                if (robot.getID() != rc.getID()) {
                    tryMove(robot.location.directionTo(currentLocation));
                    sawOthers = true;
                }
            }
            if (!sawOthers) {
                if (!tryMove(hqLocation.directionTo(currentLocation))) {
                    tryMove(randomDirection());
                }
            }
        } else {
            tryMove(hqLocation.directionTo(currentLocation));
        }
    }

    /**
     * update the refineryList if one is within sensorRadius
     *
     * @throws GameActionException
     */
    public void updateFromVision() {
        for (RobotInfo r : findNearbyFriendlies()) {
            if (r.getType() == RobotType.REFINERY) {
                refineries.add(r.getLocation());
            }
        }
    }

    /**
     * look for enemy drones and try to build a net gun if one is spotted
     *
     * @throws GameActionException
     */
    public void droneDefense() throws GameActionException {
        for (RobotInfo robot : findNearbyEnemies()) {
            if (robot.getType() == RobotType.DELIVERY_DRONE) {
                emergencySignal(robot);
                if (rc.getTeamSoup() > 650) {
                    tryBuildHighest(RobotType.NET_GUN);
                }
            }
        }
    }

    /**
     * send a message over the blockchain that a drone has been spotted
     *
     * @param robot The drone that was spotted
     * @throws GameActionException
     */
    public void emergencySignal(RobotInfo robot) throws GameActionException {
        pointOfInterest enemy = new pointOfInterest(ENEMY_DRONE_CODE, robot.getLocation(), robot.getID());
        trySendMessage(enemy);
    }

    /**
     * try to build the building on the highest possible adjacent location
     *
     * @param building RobotType of the building that needs to be build
     * @return true if built building
     * @throws GameActionException
     */
    public boolean tryBuildHighest(RobotType building) throws GameActionException {
        System.out.println("Try eco boost");
        int maxHeight = 0;
        int currentHeight = rc.senseElevation(currentLocation);
        Direction highestDir = null;
        for (Direction dir : Direction.allDirections()) {
            MapLocation tmpLoc = currentLocation.add(dir);
            if (rc.canSenseLocation(tmpLoc) && !rc.isLocationOccupied(tmpLoc) && !insideDam(tmpLoc) && !damSet.contains(tmpLoc)) {
                int tmpHeight = rc.senseElevation(tmpLoc);
                if (tmpHeight > maxHeight && Math.abs(tmpHeight - currentHeight) <= GameConstants.MAX_DIRT_DIFFERENCE) {
                    highestDir = dir;
                    maxHeight = tmpHeight;
                }
            }

        }
        if (highestDir != null) {
            System.out.println(highestDir);
            System.out.println(maxHeight);
            return tryBuild(building, highestDir);
        }
        return false;
    }

    /**
     * Try to move in the direction given, or if that's not possible try to move in every direction clockwise
     *
     * @param dir Direction in which the miner should move
     * @return true if moved
     * @throws GameActionException
     */
    public boolean tryMove(Direction dir) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(dir) && !rc.senseFlooding(currentLocation.add(dir))
                    && (allowed(currentLocation.add(dir)) || !allowed(currentLocation))) {
                rc.move(dir);
                return true;
            }
            if (turnside)
                dir = dir.rotateRight();
            else
                dir = dir.rotateLeft();
        }
        return false;
    }

    /**
     * tries to o to a refinery if there is one that is close, otherwise move to soup and build one there
     *
     * @throws GameActionException
     */
    public void goToRefinery() throws GameActionException {
        int minDist = 1000;
        MapLocation minDistLoc = null;
        if (!refineries.isEmpty()) {
            for (MapLocation m : refineries) {
                if (getDistance(m, currentLocation) < minDist) {
                    minDist = getDistance(m, currentLocation);
                    minDistLoc = m;
                }
            }
        }
        System.out.println(minDistLoc + " is " + minDist + " away");
        if (minDist <= 1) {
            tryRefine(currentLocation.directionTo(minDistLoc));
        } else if (minDist >= REFINERY_DISTANCE) {
            System.out.println(roundsWithoutRefinery);
            if (nextToSoup()) {
                buildRefinery();
            } else if (senseClosestSoup() != null) {
                moveToAdjecentLocation(senseClosestSoup());
            } else if (roundsWithoutRefinery > MAX_ROUNDS_WITHOUT_REFINERY) {
                buildRefinery();
            } else {
                roundsWithoutRefinery++;
            }
        } else if (minDist != 1000) {
            moveToLocation(minDistLoc);
        }
    }


    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    /**
     * try to build a refinery in a direction that does not hinder the dam
     *
     * @throws GameActionException
     */
    public void buildRefinery() throws GameActionException {
        for (Direction dir : Direction.allDirections()) {
            MapLocation loc = currentLocation.add(dir);
            if (rc.canSenseLocation(loc) && rc.isReady() && rc.canBuildRobot(RobotType.REFINERY, dir)
                    && !insideDam(loc) && !damSet.contains(loc)) {
                rc.buildRobot(RobotType.REFINERY, dir);
                pointOfInterest refinery = new pointOfInterest(REFINERY_CODE, currentLocation.add(dir), -1);
                trySendMessage(refinery);
            }
        }
    }

    /**
     * build a building determined by the base layout
     *
     * @param point The pointOfInterest (dataType that contains a location and building that needs to be built)
     * @return true if built
     * @throws GameActionException
     */
    public boolean tryBuildPoint(pointOfInterest point) throws GameActionException {
        MapLocation loc = point.location;
        RobotType building = codeToType(point.buildCode);
        if (currentLocation.isAdjacentTo(loc) && !currentLocation.equals(loc)) {
            if (tryBuild(building, currentLocation.directionTo(loc))) {
                return true;
            }
        } else if (currentLocation.equals(loc)) {
            tryMove(randomDirection());
        } else {
            // else go to design school location
            moveToLocation(loc);
        }
        return false;

    }

    /**
     * checks whether there is soup adjacent to the currentLocation
     *
     * @return true if adjacent to soup
     * @throws GameActionException
     */
    public boolean nextToSoup() throws GameActionException {
        for (Direction d : directions) {
            senseLocation = currentLocation.add(d);
            if (rc.canSenseLocation(senseLocation) && rc.senseSoup(senseLocation) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * try to mine soup in every direction
     *
     * @throws GameActionException
     */
    public void mineSoup() throws GameActionException {
        for (Direction d : directions) {
            tryMine(d);
        }
    }

    /**
     * checks whether a specific location is allowed to stand on
     *
     * @param m The location that needs to be checked
     * @return true if it the specific location is not reserved
     * @throws GameActionException
     */
    public boolean allowed(MapLocation m) {
        for (MapLocation l : reservedLocations) {
            if (m.equals(l))
                return false;
        }
        return true;
    }

    /**
     * initialize the hqLocation and also add it to the refineryList
     *
     * @throws GameActionException
     */
    public void initLocations() {
        for (RobotInfo robot : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (robot.type == RobotType.HQ) {
                hqLocation = robot.location;
                refineries.add(hqLocation);
                break;
            }
        }
    }
}

