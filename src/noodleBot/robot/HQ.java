package noodleBot.robot;

import battlecode.common.*;

import java.util.*;

public class HQ extends AbstractRobotPlayer {
    AbstractRobotPlayer internalNetGun;
    int amountOfMiners;
    int startMiners = 2;
    int amountOfBuildings;
    int hqHeight;
    pointOfInterest underConstruction;
    static Queue<pointOfInterest> buildQueue = new LinkedList<>();
    static Set<Integer> miners = new HashSet<>();
    static Set<Integer> landscapers = new HashSet<>();
    static Set<Integer> drones = new HashSet<>();
    static Set<Integer> designSchools = new HashSet<>();
    static Set<Integer> fulfillmentCenters = new HashSet<>();
    static Set<Integer> vaporators = new HashSet<>();
    static Set<MapLocation> buildLocations = new HashSet<>();

    boolean defenseReady = false;

    public HQ(RobotController rc) throws GameActionException {
        super(rc);
        internalNetGun = new NetGun(rc);
        team = rc.getTeam();
        hqLocation = rc.getLocation();
        System.out.println(hqLocation);
        currentLocation = rc.getLocation();
        System.out.println("location: " + hqLocation);
        buildLocations.add(hqLocation);
        hqHeight = rc.senseElevation(currentLocation);
        int builtMiners = 0;
        while (startMiners > builtMiners) {
            if (tryBuildMiner()) {
                builtMiners++;
            }
        }
        System.out.println("I have made my 2 miners");
        determineBuildingLocations();
        determineDam(DAM_RADIUS);
    }

    @Override
    protected void run() throws GameActionException {
        internalNetGun.run();
        updateDamBuildingPhase();
        updateBuildings();
        updateUnitID();
        buildMiners();
        //buildMiners_DEPRECATED(); //also checks if it is needed
    }

    /**
     * Main logic about which building is placed where, only is able to rotate the current build plan.
     * @throws GameActionException
     */
    protected void determineBuildingLocations() throws GameActionException {
        Direction DesignSchoolDir = null;
        Direction FlightCenterDir = null;
        int currentHeight = rc.senseElevation(currentLocation);
        for (Direction DSDir : Direction.cardinalDirections()) {
            MapLocation DSLoc = hqLocation.add(DSDir);
            System.out.println(DSDir);
            System.out.println(DSLoc);
            if (validSpawnBuildingLoc(DSLoc) && Math.abs(rc.senseElevation(DSLoc) - currentHeight) <= GameConstants.MAX_DIRT_DIFFERENCE) {
                pointOfInterest DS = new pointOfInterest(DS_CODE, DSLoc, -1);
                System.out.println("added DS: " + DS);
                System.out.println(DS.buildCode);
                buildQueue.add(DS);
                buildLocations.add(DSLoc);
                DesignSchoolDir = DSDir;
                break;
            }
        }
        System.out.println("The designSchool is being placed: " + DesignSchoolDir + "of me");

        if (validSpawnBuildingLoc(hqLocation.subtract(DesignSchoolDir))) {
            pointOfInterest FC = new pointOfInterest(FC_CODE, hqLocation.subtract(DesignSchoolDir), -1);
            buildQueue.add(FC);
            System.out.println("Added FC: " + FC);
            System.out.println(FC.buildCode);
            buildLocations.add(FC.location);
            FlightCenterDir = DesignSchoolDir.opposite();
            System.out.println("FlightCenter is going to be placed: " + FlightCenterDir);
        } else {
            for (Direction DSDir : Direction.cardinalDirections()) {
                MapLocation DSLoc = hqLocation.add(DSDir);
                if (validSpawnBuildingLoc(DSLoc)) {
                    pointOfInterest FC = new pointOfInterest(FC_CODE, hqLocation.subtract(DesignSchoolDir), -1);
                    buildQueue.add(FC);
                    buildLocations.add(FC.location);
                    FlightCenterDir = DesignSchoolDir.opposite();
                    break;
                }
            }
        }

        if (rc.canSenseLocation(hqLocation.add(FlightCenterDir.rotateLeft()))) {
            pointOfInterest VP = new pointOfInterest(VP_CODE, hqLocation.add(FlightCenterDir.rotateLeft()), -1);
            buildQueue.add(VP);
            buildLocations.add(VP.location);
        } else if (rc.canSenseLocation(hqLocation.add(FlightCenterDir.rotateRight()))) {
            pointOfInterest VP = new pointOfInterest(VP_CODE, hqLocation.add(FlightCenterDir.rotateRight()), -1);
            buildQueue.add(VP);
            buildLocations.add(VP.location);
        }
        System.out.println("Build queues info");
        System.out.println("size: " + buildQueue.size());
        System.out.println(buildQueue);
    }

    /**
     * adds a square of valid locations to the damSet
     *
     * @param cornerDistance - the amount of squares out the corners are
     */
    protected void determineDam(int cornerDistance) throws GameActionException {
        LinkedList<MapLocation> corners = new LinkedList();
        for (Direction dir : Direction.cardinalDirections()) {
            Direction cornerDir = dir.rotateLeft();
            MapLocation cornerLoc = hqLocation;
            for (int i = 0; i < cornerDistance; i++) {
                cornerLoc = cornerLoc.add(cornerDir);
            }
            corners.add(cornerLoc);
        }
        for (MapLocation corner : corners) {
            pointOfInterest point = new pointOfInterest(CORNER_DAM_LOC, corner, cornerDistance);
            trySendMessage(point);
            damFromCorner(corner, cornerDistance);
        }
    }

    /**
     * Checks if this location is a valid spot for a building that spawns units
     * Checks if there is an available spawnlocation
     * @param loc
     * @return true if there is an available spawnlocation
     */
    protected boolean validSpawnBuildingLoc(MapLocation loc) {
        int spawnLocs = 0;
        System.out.println("spawnlocs: " + spawnLocs);
        if (rc.canSenseLocation(loc) && !buildLocations.contains(loc)) {
            System.out.println(loc + "can sense: " + rc.canSenseLocation(loc));
            System.out.println("already occupied: " + buildLocations.contains(loc));
            for (Direction dir : Direction.allDirections()) {
                if (hqLocation.isAdjacentTo(loc.add(dir)) && dir != Direction.CENTER) {
                    System.out.println("Spawnable dir: " + dir);
                    System.out.println("Check: " + spawnLocs);
                    spawnLocs++;
                }
            }
            System.out.println(spawnLocs);
            System.out.println("valid :" + (spawnLocs >= 2));
            return (spawnLocs >= 2);
        }
        return false;
    }

    /**
     * Adds the ids of all visible units to the known unit sets
     */
    protected void updateUnitID() {
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.getTeam() == team) {
                switch (robot.getType()) {
                    case MINER:
                        miners.add(robot.getID());
                        break;
                    case DELIVERY_DRONE:
                        drones.add(robot.getID());
                        break;
                    case DESIGN_SCHOOL:
                        designSchools.add(robot.getID());
                        break;
                    case LANDSCAPER:
                        landscapers.add(robot.getID());
                        break;
                    case FULFILLMENT_CENTER:
                        fulfillmentCenters.add(robot.getID());
                        break;
                    case VAPORATOR:
                        vaporators.add(robot.getID());
                        break;
                }
            }
        }
    }

    /**
     * Tries to assign miners to the buildings that are still left in the build queue
     * @throws GameActionException
     */
    protected void updateBuildings() throws GameActionException {
        System.out.println("Build queues info");
        System.out.println("size: " + buildQueue.size());
        System.out.println(buildQueue);
        if (underConstruction == null) {
            if (!buildQueue.isEmpty()) {
                boolean success = false;
                pointOfInterest building = buildQueue.peek();
                System.out.println(building);
                switch (building.buildCode) {
                    case DS_CODE:
                        success = updateDS(building);
                        break;
                    case FC_CODE:
                        success = updateFC(building);
                        break;
                    case VP_CODE:
                        success = updateVP(building);
                        break;
                    case NG_CODE:
                        success = updateNG(building);
                        break;
                    default:
                        break;
                }
                if (success) {
                    System.out.println("Send Message to build: " + building.buildCode);
                    underConstruction = buildQueue.remove();
                } else {
                    System.out.println("Failed to build: " + building.buildCode);
                }
            } else {
                System.out.println("Build Queue is empty");
            }
        } else {
            if (rc.canSenseLocation(underConstruction.location)) {
                RobotInfo building = rc.senseRobotAtLocation(underConstruction.location);
                if (building != null && building.getType() == codeToType(underConstruction.buildCode)) {
                    underConstruction = null;
                    if (buildQueue.isEmpty()) {
                        defenseReady = true;
                    }
                }
            }
        }
    }

    /**
     * Checks and updates if necessary the build phase we are in. Currently the only phases are:
     * Not dambuilding and dambuilding.
     * @throws GameActionException
     */
    protected void updateDamBuildingPhase() throws GameActionException {
        if (!damBuildingPhase) {
            if (defenseReady || rc.getRoundNum() > START_DAM_BUILDING_ROUND) {
                damBuildingPhase = true;
                pointOfInterest evacuate = new pointOfInterest(START_DAM_BUILDING, hqLocation, -1);
                trySendMessage(evacuate);
            }
        }
    }

    /**
     * Assigns the first miner it sees to building a designSchool, after which it removes it from its buildqueue
     * if it cant see a miner it grabs a "random" known miner, it is not 100% sure
     * if this one is alive and thus not prefered.
     * (it picks a element from a set, the order of which is not known)
     *
     * @param designSchool
     * @throws GameActionException
     */
    protected boolean updateDS(pointOfInterest designSchool) throws GameActionException {
        System.out.println("DS cost: " + RobotType.DESIGN_SCHOOL.cost);
        if (rc.getTeamSoup() >= RobotType.DESIGN_SCHOOL.cost) {
            if (assignMiner(designSchool)) {
                return true;
            }
        }
        System.out.println("Not enough resources");
        return false;
    }

    /**
     * Checks if there is enough soup to build a fulfillmentCenter, and assigns a miner to build one if there is enough soup
     * @param fulfillmentCenter A point of interest of a fulfillmentCenter
     * @return
     * @throws GameActionException
     */
    protected boolean updateFC(pointOfInterest fulfillmentCenter) throws GameActionException {
        if (rc.getTeamSoup() >= RobotType.FULFILLMENT_CENTER.cost) {
            if (assignMiner(fulfillmentCenter)) {
                return true;
            }
        }
        System.out.println("Not enough resources");
        return false;
    }

    /**
     * Checks if there is enough soup to build a vaporator, and assigns a miner to build one if there is enough soup
     * @param vaporator A point of interest of a vaporator
     * @return
     * @throws GameActionException
     */
    protected boolean updateVP(pointOfInterest vaporator) throws GameActionException {
        if (rc.getTeamSoup() >= RobotType.VAPORATOR.cost) {
            if (assignMiner(vaporator)) {
                return true;
            }
        }
        System.out.println("Not enough resources");
        return false;
    }

    /**
     * Checks if there is enough soup to build a net gun, and assigns a miner to build one if there is enough soup
     * @param netGun A point of interest of a net gun
     * @return
     * @throws GameActionException
     */
    protected boolean updateNG(pointOfInterest netGun) throws GameActionException {
        if (rc.getTeamSoup() >= RobotType.NET_GUN.cost) {
            if (assignMiner(netGun)) {
                return true;
            }
        }
        System.out.println("Not enough resources");
        return false;
    }

    /**
     * Sends a build request in the blockchain with the id of nearby miner if any, otherwise assigns a random known
     * minerID
     * @param building
     * @return
     * @throws GameActionException
     */
    protected boolean assignMiner(pointOfInterest building) throws GameActionException {
        int minerID = 0;
        System.out.println("Assign miner to building: " + building.buildCode);
        for (RobotInfo robot : findNearbyFriendlies()) {
            if (robot.getType() == RobotType.MINER) {
                minerID = robot.getID();
            }
        }
        if (!miners.isEmpty()) {
            minerID = miners.iterator().next();
        }
        if (minerID != 0) {
            building.unitID = minerID;
            trySendMessage(building);
            return true;
        }
        return false;
    }

    /**
     * Checks if more miners are needed and builds them if true
     * @return
     * @throws GameActionException
     */
    protected boolean buildMiners() throws GameActionException {
        if (!damBuildingPhase) {
            // (landscapers.size() >= LANDSCAPERS_BATCH_1 && miners.size() <= MINERS_BATCH_2))
            if (miners.size() <= MINERS_BATCH_1 || miners.size() < refineries.size() * 2) {
                return tryBuildMiner();
            }
        }
        return false;
    }

    /**
     * Tries to build a miner in any direction, prefers the direction of soup
     * @return True if successful
     * @throws GameActionException
     */
    protected boolean tryBuildMiner() throws GameActionException {
        if (senseClosestSoup() != null) {
            return tryBuildMiner(currentLocation.directionTo(soupLocation));
        } else {
            return tryBuildMiner(randomDirection());
        }
    }

    /**
     * Tries to build a miner in a given direction. If not successful tries to build it in an other direction
     * @param dir
     * @return True if a miner has been build
     * @throws GameActionException
     */
    protected boolean tryBuildMiner(Direction dir) throws GameActionException {
        for (int i = 1; i < 9; i++) {
            if (!dir.equals(Direction.SOUTH) && !dir.equals(Direction.EAST) && tryBuild(RobotType.MINER, dir)) {
                amountOfMiners++;
                System.out.println("Spawned a miner direction: " + dir);
                return true;
            } else if (i % 2 == 0) {
                for (int j = 0; j < i; j++) dir = dir.rotateLeft();
            } else {
                for (int j = 0; j < i; j++) dir = dir.rotateRight();
            }
        }
        return false;
    }
}
