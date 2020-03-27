package noodleBot.robot;

import battlecode.common.*;

public class NetGun extends AbstractRobotPlayer {
    public NetGun(RobotController rc) {
        super(rc);
    }

    @Override
    protected void run() throws GameActionException {
        for(RobotInfo robot: findNearbyEnemies()){
            if(robot.getType() == RobotType.DELIVERY_DRONE){
                shoot(robot.getID());
            }
        }
    }

    protected void shoot(int id) throws GameActionException{
        if(rc.canShootUnit(id)){
            rc.shootUnit(id);
        }
    }
}
