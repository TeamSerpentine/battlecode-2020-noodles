package noodleBot.robot;

import battlecode.common.*;

import java.util.*;

public abstract strictfp class AbstractRobotPlayer {

    public RobotController rc;

    public Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };
    public RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    public int turnCount;

    // variables made by us (Noodles)
    public int DEBUG_LEVEL = 0;

    public Direction dirRC = Direction.CENTER; // direction of the robot
    public MapLocation hqLocation;
    public ArrayList<MapLocation> refineryLocations = new ArrayList<>();
    public Transaction[] lastBlockchain;
    public int[] lastMessage = new int[7];
    public boolean turnside;
    public MapLocation destination;
    public MapLocation currentLocation;

    /**
     * base variables
     */
    public int DAM_RADIUS = 2;
    public int dam_distance; //Currently always the same as DAM_RADIUS, but if dam_radius is going to be determined by HQ this is used to send it over
    public boolean damSetDetermined = false;
    public MapLocation locationDS;
    public MapLocation locationFC;
    public MapLocation locationV;
    public MapLocation soupLocation;
    public Team team;


    //tactic constants (robotCount - #refineries when batch is finished)
    public int MINERS_BATCH_1 = 4;
    public int DESIGN_SCHOOL_1 = MINERS_BATCH_1 + 1;
    public int LANDSCAPERS_BATCH_1 = 3;
    public int MINERS_BATCH_2 = DESIGN_SCHOOL_1 + LANDSCAPERS_BATCH_1 + 6;
    public int VAPORATOR_1 = MINERS_BATCH_2 + 1;
    public int FULFILLMENT_1 = VAPORATOR_1 + 1;
    public int DRONE_1 = FULFILLMENT_1 + 1;
    public int LANDSCAPERS_BATCH_2 = DRONE_1 + 20;
    public int MAX_ROUNDS_WITHOUT_REFINERY = 20;
    public int MINIMUM_DRONES_FOR_ATTACK = 4;
    public int START_DAM_BUILDING_ROUND = 400;
    public int RESERVE_SOUP = 151;

    // TODO
    // Need to send a warning message to all miners to remove move away from the hq
    // If they cant, suicide is the better option for the group collective
    public boolean damBuildingPhase = false;
    public int DRONE_ATTACK_SOUP_RESERVE = 200; //The minimal reserve of soup before a drone decides to move out of the wall

    public int REFINERY_DISTANCE = 8; //AMOUNT OF BLOCKS A REFINERY HAS TO BE AWAY TO BUILD A NEW ONE

    //Pathfinding Stack
    public Stack<Direction> pathBacklog = new Stack<>();

    //blockchain constants
    public int birthRound;
    public int mapHeight;
    public int mapWidth;
    public final int secret = 65536;
    public final int REFINERY_CODE = 4000;
    public int TEAM_CODE;
    public final int DS_CODE = 4020;
    public final int VP_CODE = 4030;
    public final int FC_CODE = 4040;
    public final int NG_CODE = 4050;
    public final int CORNER_DAM_LOC = 3000;
    public final int DIG_LOC = 3050;
    public final int START_DAM_BUILDING = 5000;
    public final int ENEMY_HQ_CODE = 6000;
    public final int ENEMY_NET_GUN_CODE = 6050;
    public final int ENEMY_DRONE_CODE = 6100;
    public int key;
    public Set<pointOfInterest> blockChainLocations;
    public Set<MapLocation> occupiedPlaces = new HashSet<>();
    public Set<MapLocation> refineries = new HashSet<>();
    public Set<MapLocation> damSet = new HashSet<>();
    public Set<MapLocation> cornerSet = new HashSet<>();
    public Set<MapLocation> digSet = new HashSet<>();
    public Queue<pointOfInterest> unitTask = new LinkedList<>();
    public Set<MapLocation> enemyNG = new HashSet<>();
    public MapLocation enemyHQ;
    int lastCheckedRound = 0;


    /**
     * Constructor
     *
     * @param rc RobotController for this robot.
     */
    public AbstractRobotPlayer(RobotController rc) {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        this.rc = rc;
        turnCount = 0;
        prepareBlockChain();
        if (DEBUG_LEVEL >= 3) {
            System.out.println("I'm a " + rc.getType() + " and I just got created!");
        }

        if (rc.getTeam() == Team.A)
            TEAM_CODE = 8540;
        else
            TEAM_CODE = 90876;
        team = rc.getTeam();
        birthRound = rc.getRoundNum();
        System.out.println(birthRound);
    }


    public void loop() throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                if (DEBUG_LEVEL >= 5) {
                    System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                }
                run();
                while (Clock.getBytecodesLeft() > 1000 && birthRound >= lastCheckedRound) {
                    System.out.println("Checking for old messages");
                    System.out.println(lastCheckedRound);
                    decodeBlock(lastCheckedRound++);
                }
                Clock.yield();

            } catch (Exception e) {
                if (DEBUG_LEVEL >= 1) {
                    System.out.println(rc.getType() + " Exception");
                }
                e.printStackTrace();
            }
        }
    }

    abstract protected void run() throws GameActionException;


    /**
     * Returns the 3 possible opposites locations
     * always in the order mirror y, yx, x
     *
     * @return MapLocation[]
     */
    public MapLocation[] possibleOpposites(MapLocation loc) {
        System.out.println("looking for Opposites");
        MapLocation[] possibleLocations = new MapLocation[3];
        int mapHeight = rc.getMapHeight();
        int mapWidth = rc.getMapWidth();
        int mirror_y;
        int mirror_x;
        mirror_x = mapWidth - loc.x;
        mirror_y = mapHeight - loc.y;
        possibleLocations[0] = new MapLocation(mirror_x, loc.y);
        possibleLocations[1] = new MapLocation(mirror_x, mirror_y);
        possibleLocations[2] = new MapLocation(loc.x, mirror_y);
        System.out.println("Found Opposites" + possibleLocations);
        return possibleLocations;
    }


    public boolean moveToAdjecentLocation(MapLocation loc) throws GameActionException {
        if (rc.canSenseLocation(loc)) {
            for (Direction dir : Direction.allDirections()) {
                MapLocation adjLoc = loc.add(dir);
                if (rc.canSenseLocation(adjLoc) && rc.isLocationOccupied(adjLoc)) {
                    return moveToLocation(loc);
                }
            }
        } else {
            return moveToLocation(loc);
        }
        return false;
    }

    /**
     * A compute intensive way to move to loc
     * tries to move towards the given location
     *
     * @param loc
     */
    public boolean moveToLocation(MapLocation loc) throws GameActionException {
        if (!loc.equals(currentLocation)) {
            Direction dir;
            System.out.println("Moving to loc" + loc);
            if (pathBacklog.isEmpty()) {
                System.out.println("Path backlog empty");
                pathBacklog = recursivePathFinding(currentLocation, loc);
            }
            if (!pathBacklog.isEmpty()) {
                System.out.println(Arrays.toString(pathBacklog.toArray()));
                dir = pathBacklog.peek();
                System.out.println("Moving " + dir);
                System.out.println(dir);
                if (tryMoveExact(dir)) {
                    pathBacklog.pop();
                    return true;
                } else if (rc.isReady()) {
                    pathBacklog = recursivePathFinding(currentLocation, loc);
                    if (tryMoveExact(pathBacklog.peek())) {
                        pathBacklog.pop();
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    public boolean moveToLocation(Stack<Direction> path) throws GameActionException {
        if (path.isEmpty()) {
            return false;
        }
        if (tryMoveExact(path.peek())) {
            path.pop();
            return true;
        } else {
            return false;
        }
    }

    public Stack<Direction> recursivePathFinding(MapLocation currentLoc, MapLocation destLoc) throws GameActionException {
        Set<MapLocation> visitedLocations = new HashSet<>();
        return recursivePathFinding(currentLoc, destLoc, visitedLocations);
    }

    public Stack<Direction> recursivePathFinding(MapLocation currentLoc, MapLocation destLoc, Set<MapLocation> visitedLocations) throws GameActionException {
        boolean checkUnit = true;
        visitedLocations.add(currentLoc);
        if (currentLoc.equals(destLoc)) {
            return new Stack<>();
        } else {
            Direction dir = currentLoc.directionTo(destLoc);
            Stack<Direction> path;
            for (Direction curDir : alternatingDir(dir)) {
                MapLocation newInter = currentLoc.add(curDir);
                if (rc.canSenseLocation(newInter) && !visitedLocations.contains(newInter)) {
                    if (canMoveFromTo(currentLoc, newInter, checkUnit)) {
                        path = recursivePathFinding(newInter, destLoc, visitedLocations);
                        path.add(curDir);
                        return path;
                    }
                } else {
                    return new Stack<>();
                }
                rc.setIndicatorLine(currentLoc, currentLoc.add(curDir), 255, 0, 0);
            }
            return null;
        }


    }

    public Direction[] alternatingDir(Direction dir) {
        Direction[] alternating = new Direction[8];
        alternating[0] = dir;
        alternating[1] = dir.rotateLeft();
        alternating[2] = dir.rotateRight();
        alternating[3] = alternating[1].rotateLeft();
        alternating[4] = alternating[2].rotateRight();
        alternating[5] = alternating[3].rotateLeft();
        alternating[6] = alternating[4].rotateRight();
        alternating[7] = dir.opposite();
        return alternating;
    }


    public boolean canMoveFromTo(MapLocation from, MapLocation to, boolean checkUnit) throws GameActionException {
        int fromHeight = rc.senseElevation(from);
        int toHeight = rc.senseElevation(to);
        RobotInfo robot = rc.senseRobotAtLocation(to);
        return (Math.abs(fromHeight - toHeight) <= GameConstants.MAX_DIRT_DIFFERENCE &&
                !rc.senseFlooding(to) && (robot == null || (!checkUnit && !robot.getType().isBuilding())));
    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    public Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    public RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryMoveExact(Direction dir) throws GameActionException {
        if (rc.canMove(dir) && rc.canSenseLocation(currentLocation.add(dir)) && !rc.senseFlooding(currentLocation.add(dir))) {
            rc.move(dir);
            return true;
        }
        System.out.println("I could not move because" + rc.canMove(dir) +
                (rc.canSenseLocation(currentLocation.add(dir)) && !rc.senseFlooding(currentLocation.add(dir))));
        System.out.println("tried move: " + dir);
        return false;
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady()) {
            if (rc.canMove(dir) && !rc.senseFlooding(rc.getLocation().add(dir))) {
                rc.move(dir);
                return true;
            }
            for (int i = 0; i < 7; i++) {
                if (turnside)
                    dir = dir.rotateRight();
                else
                    dir = dir.rotateLeft();
                if (rc.canMove(dir) && !rc.senseFlooding(rc.getLocation().add(dir))) {
                    rc.move(dir);
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Attempts to find the mapLocation and ID of nearby Enemies
     * <p>
     * Returns them in order of distance
     *
     * @return RobotInfo[]
     */
    public RobotInfo[] findNearbyEnemies() {
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] robots = rc.senseNearbyRobots(rc.getLocation(), -1, enemy);
        return robots;
    }

    /**
     * Attempts to find the mapLocation and ID of nearby Friendlies
     * <p>
     * Returns them in order of distance
     *
     * @return RobotInfo[]
     */
    public RobotInfo[] findNearbyFriendlies() {
        Team friendly = rc.getTeam();
        RobotInfo[] robots = rc.senseNearbyRobots(rc.getLocation(), -1, friendly);
        return robots;
    }

    protected void damFromCorner(MapLocation cornerLoc, int cornerDistance) throws GameActionException {
        Direction damDirection = cornerLoc.directionTo(hqLocation).rotateLeft();
        int length = cornerDistance * 2;
        MapLocation prevDam = cornerLoc;
        damSet.add(cornerLoc);
        for (int i = 0; i < length; i++) {
            prevDam = prevDam.add(damDirection);
            if (rc.onTheMap(prevDam))
                damSet.add(prevDam);
        }
    }

    protected boolean insideDam(MapLocation loc) {
        int minX = 65;
        int minY = 65;
        int maxX = -1;
        int maxY = -1;
        for (MapLocation corner : cornerSet) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        return (loc.x < maxX && loc.x > minX && loc.y < maxY && loc.y > minY);
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir  The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }


    /*
        extra functions that we (Noodles) made:
     */
    public Direction walkTowards(MapLocation pointA, MapLocation pointB) throws GameActionException {
        int xA = pointA.x;
        int yA = pointA.y;
        int xB = pointB.x;
        int yB = pointB.y;
        Direction dir;

        if (xB > xA && yB > yA) {
            dir = Direction.NORTHEAST;
        } else if (xB > xA && yB == yA) {
            dir = Direction.EAST;
        } else if (xB > xA && yB < yA) {
            dir = Direction.SOUTHEAST;
        } else if (xB == xA && yB < yA) {
            dir = Direction.SOUTH;
        } else if (xB < xA && yB < yA) {
            dir = Direction.SOUTHWEST;
        } else if (xB < xA && yB == yA) {
            dir = Direction.WEST;
        } else if (xB < xA && yB > yA) {
            dir = Direction.NORTHWEST;
        } else if (xB == xA && yB > yA) {
            dir = Direction.NORTH;
        } else {
            dir = Direction.CENTER;
        }
        return dir;
    }

    public int getDistance(MapLocation a, MapLocation b) {
        //returns the distance if there are no obstacles
        int xDistance = Math.abs(a.x - b.x);
        int yDistance = Math.abs(a.y - b.y);
        if (xDistance > yDistance)
            return xDistance;
        return yDistance;
    }

    public boolean determineDam() throws GameActionException {
        if (cornerSet.size() == 4) {
            for (MapLocation corner : cornerSet) {
                damFromCorner(corner, dam_distance);
            }
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<MapLocation> getVisionLocations() {
        ArrayList<MapLocation> visionLocation = new ArrayList<MapLocation>();
        int sensorRadius = (int) Math.ceil(Math.sqrt(rc.getCurrentSensorRadiusSquared()));
        for (int i = -sensorRadius; i > sensorRadius; i++) {
            for (int j = -sensorRadius; j > sensorRadius; j++) {
                if ((i * i + j * j) <= sensorRadius) {
                    MapLocation possibleLocation = rc.getLocation().translate(i, j);
                    if (rc.canSenseLocation(possibleLocation)) {
                        visionLocation.add(possibleLocation);
                    }
                }
            }
        }
        return visionLocation;
    }


    public MapLocation senseClosestSoup() {
        int minDist = 100;
        MapLocation minDistLoc = null;
        for (MapLocation m : rc.senseNearbySoup()) {
            if (getDistance(currentLocation, m) < minDist) {
                minDist = getDistance(currentLocation, m);
                minDistLoc = m;
            }
        }
        soupLocation = minDistLoc;
        return minDistLoc;
    }

    /**
     * to check if you're on a dam location
     *
     * @param loc check whether this loc is on the dam
     * @return true if on dam
     */
    public boolean locatedOnDam(MapLocation loc) {
        return damSet.contains(loc);
    }

    /**
     * checks if the sensable damLocations are full of landscapers
     * if a non landscaper is standing on the dam it returns falls
     *
     * @return true if full
     */
    public boolean isDamFull() throws GameActionException {
        for (MapLocation damLoc : damSet) {
            if (rc.canSenseLocation(damLoc)) {
                RobotInfo robot = rc.senseRobotAtLocation(damLoc);
                if (robot == null || robot.getType() != RobotType.LANDSCAPER) {
                    return false;
                }
            }
        }
        System.out.println("Dam is Full");
        return true;
    }

    /**
     * Tries to find the hq and update its location
     *
     * @return true - if found
     */
    public boolean findHQ() {
        for (RobotInfo robot : findNearbyFriendlies()) {
            if (robot.getType() == RobotType.HQ) {
                hqLocation = robot.getLocation();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the lowest location on the dam
     *
     * @return MapLocation
     * @throws GameActionException
     */
    public MapLocation lowestDamLoc() throws GameActionException {
        int lowestHeight = 100000; //should be unreachable
        int closest = 100000;
        MapLocation lowestLoc = currentLocation; //should get overwritten by the first damLoc
        for (MapLocation damLoc : damSet) {
            if (rc.canSenseLocation(damLoc)) {
                int damHeight = rc.senseElevation(damLoc);
                int distance = getDistance(currentLocation, damLoc);
                if (lowestHeight > damHeight) {
                    lowestLoc = damLoc;
                    lowestHeight = damHeight;
                    closest = distance;
                } else if (lowestHeight == damHeight && closest > distance) {
                    lowestLoc = damLoc;
                    closest = distance;
                    lowestHeight = damHeight;
                }
            }
        }
        return lowestLoc;
    }


    /**
     * Calculates the necessary values for blockchain encoding and decoding
     */
    public void prepareBlockChain() {
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        key = secret * mapWidth * mapHeight;
        System.out.println("Secret key: " + key);
    }

    public boolean trySendMessage(pointOfInterest point) throws GameActionException {
        int[] message = point.encodeBlockChain();
        if (rc.canSubmitTransaction(message, 1)) {
            rc.submitTransaction(message, 1);
            return true;
        }
        return false;
    }

    /**
     * Encodes a x and y coordinate in to a single int based on the mapWidth
     *
     * @param x
     * @param y
     * @return
     */
    public int xyEncoder(int x, int y) {
        return x * mapWidth + y;
    }

    /**
     * Decodes an encode xy value for this map and creates a MapLocation out of it
     *
     * @param encoded
     * @return mapLocation
     */
    public MapLocation xyDecoder(int encoded) {
        int x = encoded / mapWidth;
        int y = encoded % mapWidth;
        return new MapLocation(x, y);
    }

    /**
     * calculates the checksum over a message using secret
     *
     * @param message
     * @return checksum value
     */
    public int checkSum(int[] message) {
        int sum = 0;
        for (int i : message) {
            sum += i;
        }
        return sum % key;
    }

    /**
     * decodes the block of a specific roundNumber
     * checks if the round number is valid
     *
     * @param roundNumber
     * @return list of pointsOfInterest that were send in this block
     * @throws GameActionException
     */
    public ArrayList<pointOfInterest> decodeBlock(int roundNumber) throws GameActionException {
        ArrayList<pointOfInterest> pointsOfInterest = new ArrayList<>();
        if (roundNumber >= 1 && roundNumber < rc.getRoundNum()) {
            Transaction[] block = rc.getBlock(roundNumber);
            for (Transaction transaction : block) {
                int[] message = transaction.getMessage();
                if (checkBlockChain(message)) {
                    pointOfInterest point = decodeBlockChain(message);
                    if (point.unitID == rc.getID()) {
                        unitTask.add(point);
                    } else {
                        switch (point.buildCode) {
                            case REFINERY_CODE:
                                refineries.add(point.location);
                                occupiedPlaces.add(point.location);
                                System.out.println("Occupied places increased");
                                break;
                            case CORNER_DAM_LOC:
                                cornerSet.add(point.location);
                                dam_distance = point.unitID;
                                break;
                            case DIG_LOC:
                                digSet.add(point.location);
                                break;
                            case START_DAM_BUILDING:
                                refineries.remove(point.location);
                                damBuildingPhase = true;
                                break;
                            case ENEMY_HQ_CODE:
                                enemyHQ = point.location;
                                enemyNG.add(point.location);
                                occupiedPlaces.add(point.location);
                                break;
                            case ENEMY_NET_GUN_CODE:
                                enemyNG.add(point.location);
                                break;
                            default:
                                System.out.println("THIS DOES NOT HAVE A CASE YET: " + codeToType(point.buildCode));
                                occupiedPlaces.add(point.location);
                        }
                    }
                }
            }
        }
        return pointsOfInterest;
    }

    /**
     * used to check if a blockchain message is ours and not tempered with
     *
     * @param message
     * @return true - if it is a valid blockchain of ours
     */
    public boolean checkBlockChain(int[] message) {
        int[] important_part = Arrays.copyOfRange(message, 0, 6);
        if ((message[6] == checkSum(important_part))
                && (TEAM_CODE == message[0])) {
            int build_code = message[1];
            int xy = message[2];
            int unit_id = message[3];
            int check_build_team = message[4];
            int check_xy_unit_id = message[5];
            if ((check_build_team == TEAM_CODE * build_code) && check_xy_unit_id == unit_id * xy) {
                return true;
            }
        }
        return false;

    }

    /**
     * Decodes a message from the blockchain
     *
     * @param message
     * @return
     */
    public pointOfInterest decodeBlockChain(int[] message) {
        return new pointOfInterest(message[1], xyDecoder(message[2]), message[3]);
    }

    /**
     * Used to store points of interest to be send via the blockchain
     */
    public class pointOfInterest {
        public int buildCode;
        public MapLocation location;
        public int unitID;

        pointOfInterest(int buildCode, MapLocation location, int unitID) {
            this.buildCode = buildCode;
            this.location = location;
            this.unitID = unitID;
        }

        /**
         * Encodes this pointOfInterest in to a valid message for the blockChain
         *
         * @return a message for the blockChain
         */
        public int[] encodeBlockChain() {
            int[] message = new int[7];
            int xy = xyEncoder(location.x, location.y);
            message[0] = TEAM_CODE;
            message[1] = buildCode;
            message[2] = xy;
            message[3] = unitID;
            message[4] = TEAM_CODE * buildCode;
            message[5] = xy * unitID;
            message[6] = checkSum(message);
            return message;
        }

    }

    public RobotType codeToType(int buildingCode) {
        switch (buildingCode) {
            case REFINERY_CODE:
                return RobotType.REFINERY;
            case DS_CODE:
                return RobotType.DESIGN_SCHOOL;
            case FC_CODE:
                return RobotType.FULFILLMENT_CENTER;
            case VP_CODE:
                return RobotType.VAPORATOR;
            case NG_CODE:
                return RobotType.NET_GUN;
            default:
                return null;
        }
    }
}

