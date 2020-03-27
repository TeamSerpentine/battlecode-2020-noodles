package noodleBot.robot;

import battlecode.common.*;
import gnu.trove.list.linked.TLinkedList;

import java.util.LinkedList;

public class DesignSchool extends AbstractRobotPlayer {

    static int amountOfLS;

    static MapLocation flightCenter;

    public DesignSchool(RobotController rc) {
        super(rc);
        currentLocation = rc.getLocation();
        for (RobotInfo r : findNearbyFriendlies()) {
            if (r.getType() == RobotType.FULFILLMENT_CENTER)
                flightCenter = r.getLocation();
            else if (r.getType() == RobotType.HQ)
                hqLocation = r.getLocation();
        }
    }

    @Override
    protected void run() throws GameActionException {
        if (needMoreLS()) {
            for (Direction dir : allowedDirs()) {
                if (currentLocation.add(dir).isAdjacentTo(hqLocation) && tryBuild(RobotType.LANDSCAPER, dir)) {
                    System.out.println("Build Landscaper");
                }
            }
        }
    }

    /**
     * determines the need for more landscapers
     *
     * @return true - if there are less than the wanted starting amount of landscapers in sensorRadius
     * or if there is already a drone and enough soup
     * @throws GameActionException
     */
    protected boolean needMoreLS() throws GameActionException {
        amountOfLS = 0;
        allowedDirs();
        for (RobotInfo r : rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), team)) {
            if (r.getType().equals(RobotType.LANDSCAPER)) amountOfLS++;
        }
        if (amountOfLS < LANDSCAPERS_BATCH_1) return true;
        else if (rc.getTeamSoup() > 300) {
            for (RobotInfo r : rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), team)) {
                if (r.getType().equals(RobotType.DELIVERY_DRONE)) return true;
            }
        }
        return false;
    }

    /**
     * determines the directions in which the Design School may place landscapers
     *
     * @return a linked list with all the directions that are not reserved for other units
     * @throws GameActionException
     */
    protected LinkedList<Direction> allowedDirs() {
        LinkedList<Direction> allowed = new LinkedList<>();
        for (Direction dir : Direction.allDirections()) {
            if (currentLocation.add(dir).isAdjacentTo(hqLocation) && !isReserved(currentLocation.add(dir))) {
                allowed.add(dir);
            }
        }
        return allowed;
    }

    /**
     * checks if a specific location isn't reserved for units other than landscapers
     *
     * @param location that needs to be checked
     * @return true - if reserved for other unit
     * @throws GameActionException
     */
    protected boolean isReserved(MapLocation location) {
        return occupiedPlaces.contains(location);
    }
}
