package noodleBot.robot;

import battlecode.common.*;


public class Landscaper extends AbstractRobotPlayer {

    public boolean hasBeenOnDam = false;
    public int hqHeight;
    public MapLocation droneLoc;
    public MapLocation bestLoc;

    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        //Walk randomly until found the own HQ
        while (hqLocation == null) {
            if (findHQ()) {
                determineDam();
            } else {
                tryMove(randomDirection());
            }
            hqHeight = rc.senseElevation(hqLocation);
        }
        team = rc.getTeam();
    }

    @Override
    protected void run() throws GameActionException {
        update();

        if (rc.isReady()) {
            if (tryDefenseDig()) {
                System.out.println("Defended HQ");
            } else if (tryOffenseDeposit()) {
                System.out.println("Deposited Offensive");
            } else if (tryEqualizeHQ()) {
                System.out.println("Trying to Equalize");
            } else if (!damSetDetermined) {
                damSetDetermined = determineDam();
            } else if (tryToMove()) {
                System.out.println("Trying to move");
            } else if ((bestDamLocation().equals(currentLocation) || bestLoc.isAdjacentTo(currentLocation))) {
                if (damBuildingPhase) tryBuildOnDam();
            } else if (locLeftOfHQ(bestLoc)) {
                walkSaveLeftOfHQ();
                System.out.println("walking left of HQ");
            } else {
                walkSaveRightOfHQ();
                System.out.println("walking right of HQ");
            }
        }
    }

    /**
     * Updates local variables needed for multiple functions within Landscaper
     * @throws GameActionException
     */
    public void update() throws GameActionException {
        currentLocation = rc.getLocation();
        if (!damBuildingPhase && (rc.getRoundNum() > START_DAM_BUILDING_ROUND) || canSenseVap()) {
            damBuildingPhase = true;
        }
        if (!hasBeenOnDam) {
            hasBeenOnDam = locatedOnDam(currentLocation);
        }
        if(!damSetDetermined){
            damSetDetermined = determineDam();
        }
        if (droneLoc == null) {
            for (RobotInfo r : rc.senseNearbyRobots(-1, team)) {
                if (r.getType() == RobotType.DELIVERY_DRONE) {
                    droneLoc = r.getLocation().add(hqLocation.directionTo(r.getLocation()));
                    break;
                }
            }
        }
    }

    /**
     * tries to undig the HQ
     *
     * @return true - if successful
     * @throws GameActionException
     */
    public boolean tryDefenseDig() throws GameActionException {
        if (currentLocation.isAdjacentTo(hqLocation)) {
            Direction HQDir = currentLocation.directionTo(hqLocation);
            if (rc.canDigDirt(HQDir)) {
                rc.digDirt(HQDir);
                return true;
            } else {
                for (RobotInfo robot : rc.senseNearbyRobots(3, team)) {
                    if (robot.getType().isBuilding()) {
                        Direction dir = currentLocation.directionTo(robot.getLocation());
                        if (rc.canDigDirt(dir)) {
                            rc.digDirt(dir);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * tries to deposit dirt on an enemy building
     *
     * @return true - if successful
     * @throws GameActionException
     */
    public boolean tryOffenseDeposit() throws GameActionException {
        for (RobotInfo robot : findNearbyEnemies()) {
            if (robot.getLocation().isAdjacentTo(currentLocation)) {
                if (robot.getType().isBuilding()) {
                    tryToDepositDirt(currentLocation.directionTo(robot.getLocation()));
                    return true;
                } else {
                    for (Direction dir : directions) {
                        if (rc.canDigDirt(dir)) {
                            rc.digDirt(dir);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * tries to deposit dirt on an adjacent dam position
     *
     * @return true - if successfull
     * @throws GameActionException
     */
    public boolean tryBuildOnDam() throws GameActionException {
        System.out.println("trying to deposit on dam");
        if (rc.getDirtCarrying() <= 0) {
            if (hasBeenOnDam) {
                //dig away from the hq
                if (tryToDigDirt(hqLocation.directionTo(currentLocation))) return true;
                for (Direction d : directions) {
                    if (getDistance(currentLocation.add(d), hqLocation) == 3) {
                        if (tryToDigDirt(d)) return true;
                    }
                }
                return false;
            } else {
                if (isDrone(currentLocation)) {
                    for (RobotInfo r : rc.senseNearbyRobots(3, team)) {
                        if (!r.getType().isBuilding() && !r.getLocation().equals(currentLocation))
                            return tryToDigDirt(currentLocation.directionTo(r.getLocation()));
                    }
                    return false;
                } else {
                    return tryToDigDirt(Direction.CENTER);
                }
            }
        } else {
            System.out.println("Deposited on dam");
            Direction damDir = lowestDamDirection();
            System.out.println("Deposited" + damDir);
            return tryToDepositDirt(damDir);
        }
    }

    /**
     * landscaper tries to deposit dirt in given direction
     *
     * @param dir to deposit
     * @return true if deposited dirt
     * @throws GameActionException
     */
    public boolean tryToDepositDirt(Direction dir) throws GameActionException {
        System.out.println("Trying to deposit dirt in " + dir);
        if (rc.isReady() && rc.canDepositDirt(dir)) {
            rc.depositDirt(dir);
            return true;
        } else {
            return false;
        }
    }

    /**
     * landscaper tries to dig dirt in given direction
     *
     * @param dir to dig
     * @return true if digged dirt
     * @throws GameActionException
     */
    public boolean tryToDigDirt(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDigDirt(dir)) {
            rc.digDirt(dir);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the direction where the (reachable) dam is lowest
     *
     * @return
     * @throws GameActionException
     */
    public Direction lowestDamDirection() throws GameActionException {
        int lowestHeight;
        Direction lowestDir = Direction.CENTER; //should get overwritten by the first damLoc
        if (hasBeenOnDam) {
            lowestHeight = rc.senseElevation(currentLocation);
        } else {
            lowestHeight = 10000; //everything should be lower then here
        }
        for (Direction dir : Direction.allDirections()) {
            MapLocation loc = currentLocation.add(dir);
            if (rc.canSenseLocation(loc) && locatedOnDam(loc)) {
                int damHeight = rc.senseElevation(loc);
                System.out.println(lowestHeight + ">" + damHeight);
                if (lowestHeight > damHeight) {
                    lowestDir = dir;
                    lowestHeight = damHeight;
                    System.out.println("Lowest direction on the dam:" + lowestDir + " with a height of:" + lowestHeight + "location:" + loc);
                }
            }
        }
        System.out.println("found the lowest direction on the dam:" + lowestDir + " with a height of:" + lowestHeight);
        return lowestDir;
    }

    /**
     * tries to equalize the area around the hq to make it walkable, returns true if it is progressing or succeeded
     * @return
     * @throws GameActionException
     */
    public boolean tryEqualizeHQ() throws GameActionException {
        if (rc.getDirtCarrying() > 1 && tryEqualizeDeposit()) {
            return true;
        } else if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit) {
            return tryEqualizeDig();
        } else if (rc.getDirtCarrying() >= RobotType.LANDSCAPER.dirtLimit) {
            return tryBuildOnDam();
        }
        return true;
    }


    /**
     * Digs dirt from the higher then target height adjacent location to equalise the area around the HQ
     * @return
     * @throws GameActionException
     */
    public boolean tryEqualizeDig() throws GameActionException {
        for (Direction dir : Direction.allDirections()) {
            MapLocation loc = currentLocation.add(dir);
            if (loc.isAdjacentTo(hqLocation) && rc.canSenseLocation(loc)) {
                if (rc.senseElevation(loc) - hqHeight > GameConstants.MAX_DIRT_DIFFERENCE) {
                    if (tryToDigDirt(dir)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Tries to deposit dirt on the lower then target height, adjacent location
     * @return
     * @throws GameActionException
     */
    public boolean tryEqualizeDeposit() throws GameActionException {
        for (Direction dir : Direction.allDirections()) {
            MapLocation loc = currentLocation.add(dir);
            if (loc.isAdjacentTo(hqLocation) && rc.canSenseLocation(loc)) {
                if (hqHeight - rc.senseElevation(loc) > GameConstants.MAX_DIRT_DIFFERENCE) {
                    if (tryToDepositDirt(dir)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Neighbouring DamLocation with lowest elevation
     *
     * @return Neighbouring MapLocation with lowest elevation
     * @throws GameActionException
     */
    public MapLocation lowestNeighbourLocation() throws GameActionException {
        int lowestHeight = 10000;
        MapLocation lowestLoc = null;
        for (Direction d : directions) {
            MapLocation loc = currentLocation.add(d);
            if (rc.canSenseLocation(loc) && locatedOnDam(loc)) {
                int damHeight = rc.senseElevation(loc);
                if (lowestHeight > damHeight) {
                    lowestLoc = loc;
                    lowestHeight = damHeight;
                }
            }
        }
        return lowestLoc;
    }

    /**
     * Decides the best loc to build on the dam
     *
     * @return DamLocation with lowest elevation or neighbour Location
     * @throws GameActionException
     */
    public MapLocation bestDamLocation() throws GameActionException {
        MapLocation damLoc = lowestDamLoc();
        MapLocation neighbourLoc = lowestNeighbourLocation();
        if (rc.senseElevation(damLoc) + 2 < rc.senseElevation(neighbourLoc)) {
            bestLoc = damLoc;
            System.out.println("walking to: " + bestLoc);
            return damLoc;
        }
        bestLoc = neighbourLoc;
        System.out.println("trying to build dike: " + bestLoc);
        return neighbourLoc;
    }

    /**
     * Determines if the given location is on the left side of the hq
     * @param m - the location tested on direction relative to the hq
     * @return true - location is left of hq
     * @throws GameActionException
     */
    public boolean locLeftOfHQ(MapLocation m) throws GameActionException {
        Direction dir = currentLocation.directionTo(m);
        Direction hqDir = currentLocation.directionTo(hqLocation);
        for (int i = 0; i < 4; i++) {
            if (hqDir.equals(dir)) {
                System.out.println(m + "left of hq");
                return true;
            }
            dir = dir.rotateRight();
        }
        return false;
    }

    /**
     * walk over the dam on the left side of the hq
     *
     * @return true - if moved
     * @throws GameActionException
     */
    public boolean walkLeftOfHQ() throws GameActionException {
        Direction hqDir = currentLocation.directionTo(hqLocation);
        for (int i = 0; i < 4; i++) {
            if (locatedOnDam(currentLocation.add(hqDir)) && tryMoveLS(hqDir)) return true;
            hqDir = hqDir.rotateLeft();
        }
        return false;
    }

    /**
     * walk over the dam on the left side of the hq without risking being picked up by a drone
     *
     * @return true - if moved
     * @throws GameActionException
     */
    public boolean walkSaveLeftOfHQ() throws GameActionException {
        Direction hqDir = currentLocation.directionTo(hqLocation);
        for (int i = 0; i < 4; i++) {
            if (locatedOnDam(currentLocation.add(hqDir))) {
                tryMoveLS(hqDir);
                return true;
            }
            hqDir = hqDir.rotateLeft();
        }
        return false;
    }

    /**
     * walk over the dam on the right side of the hq without risking being picked up by a drone
     *
     * @return true - if moved
     * @throws GameActionException
     */
    public boolean walkSaveRightOfHQ() throws GameActionException {
        Direction hqDir = currentLocation.directionTo(hqLocation);
        for (int i = 0; i < 4; i++) {
            if (locatedOnDam(currentLocation.add(hqDir))) {
                tryMoveLS(hqDir);
                return true;
            }
            hqDir = hqDir.rotateRight();
        }
        return false;
    }


    /**
     * walk over the dam on the right side of the hq
     *
     * @return true - if moved
     * @throws GameActionException
     */
    public boolean walkRightOfHQ() throws GameActionException {
        Direction hqDir = currentLocation.directionTo(hqLocation);
        for (int i = 0; i < 4; i++) {
            if (locatedOnDam(currentLocation.add(hqDir)) && tryMoveLS(hqDir)) return true;
            hqDir = hqDir.rotateRight();
        }
        return false;
    }

    /**
     * walk away for more LS if needed
     *
     * @return true - if the LS needs to move
     * @throws GameActionException
     */
    public boolean tryToMove() throws GameActionException {
        System.out.println("droneLoc: " + droneLoc);
        if (rc.getRoundNum() % 30 != 0 && isLS()) return false;
        else if (rc.getRoundNum() % 150 == 0 && are2LS()) {
            if (locLeftOfHQ(droneLoc)) return walkRightOfHQ();
            else return walkLeftOfHQ();
        }
        else if (locLeftOfHQ(droneLoc)) return walkSaveRightOfHQ();
        else return walkSaveLeftOfHQ();
    }

    /**
     * check if there is a friendly drone next to the tile that the LS could move to
     *
     * @return true - if there is a drone
     * @throws GameActionException
     */
    public boolean isDrone(MapLocation m) {
        for (RobotInfo r : rc.senseNearbyRobots(m, 3, team)) {
            if (r.getType().equals(RobotType.DELIVERY_DRONE)) return true;
        }
        return false;
    }

    /**
     * check if there is a enemy drone next to the tile that the LS could move to
     *
     * @return true - if there is a drone
     * @throws GameActionException
     */
    public boolean isEnemyDrone(MapLocation m) {
        for (RobotInfo r : rc.senseNearbyRobots(m, 3, team.opponent())) {
            if (r.getType().equals(RobotType.DELIVERY_DRONE)) return true;
        }
        return false;
    }

    /**
     * check if there is a LS in a cardinal direction
     *
     * @return true - if there is a LS
     * @throws GameActionException
     */
    public boolean isLS() throws GameActionException {
        for (Direction d : Direction.cardinalDirections()) {
            RobotInfo r = rc.senseRobotAtLocation(currentLocation.add(d));
            if (r != null && r.getType().equals(RobotType.LANDSCAPER))
                return true;
        }
        return false;
    }

    /**
     * check if there are 2 LS in a cardinal direction
     *
     * @return true - if there are 2 LS
     * @throws GameActionException
     */
    public boolean are2LS() throws GameActionException {
        int LSCount = 0;
        for (RobotInfo r : rc.senseNearbyRobots(3, team)) {
            if (r != null && r.getType().equals(RobotType.LANDSCAPER))
                LSCount++;
        }
        if (LSCount > 1) return true;
        return false;
    }

    /**
     * Attempts to move in a given direction avoiding drones.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    public boolean tryMoveLS(Direction dir) throws GameActionException {
        if (rc.canMove(dir) && !rc.senseFlooding(currentLocation.add(dir)) && !isEnemyDrone(currentLocation.add(dir))) {
            rc.move(dir);
            return true;
        }
        System.out.println("I could not move because" + rc.canMove(dir) + !rc.senseFlooding(currentLocation.add(dir)));
        System.out.println("tried move: " + dir);
        return false;
    }

    /**
     * check if there is a Vaporator
     *
     * @return true - if there is a drone
     * @throws GameActionException
     */
    public boolean canSenseVap() {
        for (RobotInfo r : rc.senseNearbyRobots(-1, team)) {
            if (r.getType().equals(RobotType.VAPORATOR)) return true;
        }
        return false;
    }
}