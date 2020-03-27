package noodleBot;
import battlecode.common.*;
import noodleBot.robot.*;

import java.util.ArrayList;

import java.util.Arrays;

public strictfp class RobotPlayer {   /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        AbstractRobotPlayer robot;
        switch (rc.getType()) {
            case HQ:                 robot = new HQ(rc);                break;
            case MINER:              robot = new Miner(rc);             break;
            case REFINERY:           robot = new Refinery(rc);          break;
            case VAPORATOR:          robot = new Vaporator(rc);         break;
            case DESIGN_SCHOOL:      robot = new DesignSchool(rc);      break;
            case FULFILLMENT_CENTER: robot = new FulfillmentCenter(rc); break;
            case LANDSCAPER:         robot = new Landscaper(rc);        break;
            case DELIVERY_DRONE:     robot = new DeliveryDrone(rc);     break;
            case NET_GUN:            robot = new NetGun(rc);            break;
            default:                 throw new IllegalStateException();
        }
        robot.loop();
    }
}
