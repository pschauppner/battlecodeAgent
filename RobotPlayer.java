package battlecodeAgent;
import battlecode.common.*;
import battlecode.server.GameInfo;

import java.awt.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        RobotPlayer.rc = rc;

        //Choose method based on robot type
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
	}

    static void runArchon() throws GameActionException {

        /**
         *  Things and Archon can do
         *  Sense           - neutral trees close (for shake), enemy robots (for move)
         *  Shake           - shakes neutral trees that have bullets within shake radius
         *  Hire Gardener   - at set probability, when ready hire gardener at first free space. If no space, set cramped flag (for move)
         *  Move            - 1 - run away from visible enemy, 2 - make space to hire robot (dumb), 3 - do nothing
         *  Donate          - 1 - donate enough to win game, 2 - buy 1 VP if excess bullets (excess lowers throughout game)
         *  Broadcast       - not implemented
        **/

        double GARDENER_HIRE_PROBABILITY = .05;     //TEAM STRAGEGY SETTING
        boolean tooCramped = false;                 //Indicates unable to hire gardener due to surrounding objects
        //Main Loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////
                TreeInfo [] nearbyNeutralTrees = rc.senseNearbyTrees(RobotType.ARCHON.strideRadius*2,Team.NEUTRAL);
                RobotInfo[] visibleEnemies = rc.senseNearbyRobots(RobotType.ARCHON.sensorRadius*2,rc.getTeam().opponent());

                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////
                //shake nearby neutral trees with bullets
                for(int i = 0; i < nearbyNeutralTrees.length ; i++)
                {
                    if(rc.canShake(nearbyNeutralTrees[i].ID) && nearbyNeutralTrees[i].containedBullets > 0)
                    {
                        rc.shake(nearbyNeutralTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////        HIRE GARDENER      //////
                //////////////////////////////////////
                //At some probability, if ready, try to hire gardener in open space around self
                //starts with random direction and searches in random direction 10 intervals around circle
                if(rc.isBuildReady() && Math.random() < GARDENER_HIRE_PROBABILITY)
                {
                    double hireAngle = Math.random() * 6.28;
                    double searchDirection = Math.random() - .5;
                    searchDirection = searchDirection / Math.abs(searchDirection);

                    for(int i = 0; i < 10; i++)
                    {
                        Direction hireDir = new Direction((float)(hireAngle + searchDirection * i * .628));
                        if(rc.canHireGardener(hireDir))
                        {
                            tooCramped = false;
                            rc.hireGardener(hireDir);
                            break;
                        }
                        tooCramped = true;
                    }
                }

                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                //tries to move in a direction away from first enemy it sees
                //if there are no visible enemies, checks if these is space to hire gardener - if not, move randomly
                //checks to see if it is still too cramped
                if(tooCramped)
                {
                    for (float d = 0; d < 6.28; d += .6)
                    {
                        Direction testDirection = new Direction(d);
                        if (rc.canHireGardener(testDirection))
                        {
                            tooCramped = false;
                            break;
                        }
                        tooCramped = true;
                    }
                }
                if(visibleEnemies.length > 0)
                {
                    Direction runAwayDir = new Direction(visibleEnemies[0].getLocation(), rc.getLocation());
                    if(!tryMove(runAwayDir))
                        tryMove(randomDirection());
                }
                else if(tooCramped)
                    tryMove(randomDirection());



                //////////////////////////////////////
                /////           DONATE          //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////         BROADCAST         //////
                //////////////////////////////////////

                //BROADCAST IMPLEMENTATION HERE

                //////////////////////////////////////
                /////         END TURN          //////
                //////////////////////////////////////
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        /**
         * Things a Gardener can do
         * Sense           - shakeable trees (for shake), waterable trees (for water), nearby robots & finds first friendly archon & enemy (for move)
         * Shake           - shakes a tree if it is close enough and has bullets
         * Water           - stops moving and waters tree until close to max health
         * Construct       - at some probability tries to plant tree - if that fails, at some probability try to build lumberjack
         * Move            - 1-moves between enemy & archon if see's both, move away from enemy or archon if sees one, otherwise random (if move fails, move random)
         * Donate          - end game if able, else buy 1 VP if excess bullets, excess lowers as game goese on
         * Broadcast       - not implemented
        **/
        double PLANT_TREE_PROBABILITY = .05;    //TEAM STRATEGY FACTOR
        double BUILD_LUMB_PROBABILITY = .1;    //TEAM STRATEGY FACTOR
        double BUILD_TANK_PROBABILITY = .05;
        double BUILD_SOLD_PROBABILITY = 0;//.01;
        double BUILD_SCOUT_PROBABILITY = .05;


        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                boolean moveFlag = true;
                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////

                TreeInfo[] shakeableTrees = rc.senseNearbyTrees(RobotType.GARDENER.strideRadius*2,Team.NEUTRAL);
                TreeInfo[] waterableTrees = rc.senseNearbyTrees(RobotType.GARDENER.sensorRadius,rc.getTeam());
                RobotInfo[] visibleRobots = rc.senseNearbyRobots(RobotType.GARDENER.sensorRadius*2);
                int myArchonIndex = -1;
                int visibleEnemyIndex = -1;
                for(int i=0; i<visibleRobots.length ; i++)
                {
                    if(visibleRobots[i].getType() == RobotType.ARCHON && visibleRobots[i].getTeam() == rc.getTeam())
                        myArchonIndex = i;
                    else if(visibleRobots[i].getTeam() == rc.getTeam().opponent())
                        visibleEnemyIndex = i;
                    if(myArchonIndex > 0 && visibleEnemyIndex > 0)
                        break;
                }


                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////
                //shakes first neutral tree it can
                for(int i = 0; i < shakeableTrees.length ; i++)
                {
                    if(rc.canShake(shakeableTrees[i].ID) && shakeableTrees[i].containedBullets > 0)
                    {
                        rc.shake(shakeableTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////           WATER           //////
                //////////////////////////////////////
                //water's first tree it can if tree HP < maxTreeHealth - waterHealingPower
                System.out.println(waterableTrees.length);
                for(int i = 0; i < waterableTrees.length ; i++)
                {
                    //if(rc.canWater(waterableTrees[i].ID) && waterableTrees[i].health < (GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE))
                    if(rc.canWater(waterableTrees[i].ID) && waterableTrees[i].health < (GameConstants.BULLET_TREE_MAX_HEALTH))
                    {
                        rc.water(waterableTrees[i].ID);
                        moveFlag = false;
                        break;
                    }
                }

                //////////////////////////////////////
                /////         CONSTRUCT         //////
                //////////////////////////////////////
                // Randomly attempt to build a tree in a random direction. If tree fails try lumberjack

                double buildAngle = Math.random() * 6.28;
                double searchDirection = Math.random() - .5;
                searchDirection = searchDirection / Math.abs(searchDirection);
                if(Math.random() < PLANT_TREE_PROBABILITY && /*rc.isBuildReady() &&*/ rc.getTeamBullets() > GameConstants.BULLET_TREE_COST)
                {
                    for(int i = 0; i < 10; i++)
                    {
                        Direction plantDir = new Direction((float)(buildAngle + searchDirection * i * .628));
                        if(rc.canPlantTree(plantDir))
                        {
                            rc.plantTree(plantDir);
                            break;
                        }
                    }
                }
                else if (Math.random() < BUILD_LUMB_PROBABILITY && rc.isBuildReady() && rc.getTeamBullets() > RobotType.LUMBERJACK.bulletCost)
                {
                    for(int i = 0; i < 10; i++)
                    {
                        Direction buildDir = new Direction((float)(buildAngle + searchDirection * i * .628));
                        if(rc.canBuildRobot(RobotType.LUMBERJACK,buildDir));
                        {
                            rc.buildRobot(RobotType.LUMBERJACK,buildDir);
                            break;
                        }
                    }
                }
                else if (Math.random() < BUILD_TANK_PROBABILITY && rc.isBuildReady() && rc.getTeamBullets() > RobotType.TANK.bulletCost)
                {
                    for(int i = 0; i < 10; i++)
                    {
                        Direction buildDir = new Direction((float)(buildAngle + searchDirection * i * .628));
                        if(rc.canBuildRobot(RobotType.TANK,buildDir));
                        {
                            rc.buildRobot(RobotType.TANK,buildDir);
                            break;
                        }
                    }
                }
                else if (Math.random() < BUILD_SCOUT_PROBABILITY && rc.isBuildReady() && rc.getTeamBullets() > RobotType.SCOUT.bulletCost)
                {
                    for(int i = 0; i < 10; i++)
                    {
                        Direction buildDir = new Direction((float)(buildAngle + searchDirection * i * .628));
                        if(rc.canBuildRobot(RobotType.SCOUT,buildDir));
                        {
                            rc.buildRobot(RobotType.SCOUT,buildDir);
                            break;
                        }
                    }
                }
                else if (Math.random() < BUILD_SOLD_PROBABILITY && rc.isBuildReady() && rc.getTeamBullets() > RobotType.SOLDIER.bulletCost)
                {
                    for(int i = 0; i < 10; i++)
                    {
                        Direction buildDir = new Direction((float)(buildAngle + searchDirection * i * .628));
                        if(rc.canBuildRobot(RobotType.SOLDIER,buildDir));
                        {
                            rc.buildRobot(RobotType.SOLDIER,buildDir);
                            break;
                        }
                    }
                }
                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                if(moveFlag)
                {
                    Direction moveDirection = randomDirection();
                    if(visibleEnemyIndex > 0 && myArchonIndex > 0)  //SEE BOTH
                    {
                        float midX = (visibleRobots[visibleEnemyIndex].getLocation().x + visibleRobots[myArchonIndex].getLocation().x)/2;
                        float midY = (visibleRobots[visibleEnemyIndex].getLocation().y + visibleRobots[myArchonIndex].getLocation().y)/2;
                        MapLocation midPoint = new MapLocation(midX,midY);
                        moveDirection = new Direction(rc.getLocation(),midPoint);
                    }
                    else if(visibleEnemyIndex > 0)
                        moveDirection = new Direction(visibleRobots[visibleEnemyIndex].getLocation(),rc.getLocation());
                    else if(myArchonIndex > 0)
                        moveDirection = new Direction(visibleRobots[myArchonIndex].getLocation(),rc.getLocation());
                    if(!tryMove(moveDirection))
                        tryMove(randomDirection());
                }

                //////////////////////////////////////
                /////           DONATE          //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////          BROADCAST        //////
                //////////////////////////////////////

                //BROADCAST IMPLEMNTATION HERE

                //////////////////////////////////////
                /////          END TURN         //////
                //////////////////////////////////////
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                boolean moveFlag = true;
                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////

                TreeInfo[] visibleTrees = rc.senseNearbyTrees(RobotType.SOLDIER.sensorRadius*2);
                int maxIndex = visibleTrees.length;
                float maxDist = RobotType.SOLDIER.sensorRadius*2;
                int iocRT = maxIndex;
                int iocBT = maxIndex;
                int iocET = maxIndex;
                int iocNT = maxIndex;
                float dtcRT = maxDist;
                float dtcBT = maxDist;
                float dtcET = maxDist;
                float dtcNT = maxDist;

                for(int i =0;i<maxIndex;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleTrees[i].getLocation());
                    if(visibleTrees[i].containedRobot != null)
                    {
                        if(iDist < dtcRT)
                        {
                            iocRT = i;
                            dtcRT = iDist;
                        }
                    }
                    if(visibleTrees[i].getContainedBullets() > 0)
                    {
                        if(iDist < dtcBT)
                        {
                            iocBT = i;
                            dtcBT = iDist;
                        }
                    }
                    if(visibleTrees[i].team == rc.getTeam().opponent())
                    {
                        if(iDist < dtcET)
                        {
                            iocET = i;
                            dtcET = iDist;
                        }
                    }
                    else if(visibleTrees[i].team == Team.NEUTRAL)
                    {
                        if(iDist < dtcNT)
                        {
                            iocNT = i;
                            dtcNT = iDist;
                        }
                    }
                }

                RobotInfo[] visibleEnemies = rc.senseNearbyRobots(RobotType.SOLDIER.sensorRadius*2,rc.getTeam().opponent());
                int maxEIndex = visibleEnemies.length;
                float maxEDist = RobotType.SOLDIER.sensorRadius*2;
                int iocE = maxEIndex;
                float dtcE = maxEDist;

                for(int i = 0;i<visibleEnemies.length;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleEnemies[i].getLocation());
                    if(iDist < dtcE)
                    {
                        iocE = i;
                        dtcE = iDist;
                    }
                }

                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.SOLDIER.strideRadius*2);


                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////

                for(int i = 0; i < nearbyTrees.length ; i++) {
                    if (rc.canShake(nearbyTrees[i].ID) && nearbyTrees[i].containedBullets > 0)
                    {
                        rc.shake(nearbyTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////           ATTACK          //////
                //////////////////////////////////////

                RobotInfo[] strikeableRobots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS,rc.getTeam().opponent());
                if(strikeableRobots.length > 0 && rc.canFireSingleShot())
                {
                    rc.fireSingleShot(rc.getLocation().directionTo(strikeableRobots[0].getLocation()));
                    moveFlag = false;
                }

                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                if(moveFlag)
                {
                    if (visibleTrees.length == 0 && visibleEnemies.length == 0)
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                    else if (iocE < maxEIndex)
                        tryMove(rc.getLocation().directionTo(visibleEnemies[iocE].getLocation()));
                    /*else if (iocRT < maxIndex)
                        tryMove(visibleTrees[iocRT].getLocation().directionTo(rc.getLocation()));
                    else if (iocET < maxIndex)
                        tryMove(visibleTrees[iocET].getLocation().directionTo(rc.getLocation()));
                    else if (iocNT < maxIndex)
                        tryMove(visibleTrees[iocNT].getLocation().directionTo(rc.getLocation()));
                    else if (iocBT < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocBT].getLocation()));
                        */
                    else
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                }
                //////////////////////////////////////
                /////           DONATE          //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////          BROADCAST        //////
                //////////////////////////////////////

                //BROADCAST IMPLEMNTATION HERE

                //////////////////////////////////////
                /////          END TURN         //////
                //////////////////////////////////////
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("I'm a tank!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                boolean moveFlag = true;
                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////

                TreeInfo[] visibleTrees = rc.senseNearbyTrees(RobotType.TANK.sensorRadius*2);
                int maxIndex = visibleTrees.length;
                float maxDist = RobotType.TANK.sensorRadius*2;
                int iocRT = maxIndex;
                int iocBT = maxIndex;
                int iocET = maxIndex;
                int iocNT = maxIndex;
                float dtcRT = maxDist;
                float dtcBT = maxDist;
                float dtcET = maxDist;
                float dtcNT = maxDist;

                for(int i =0;i<maxIndex;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleTrees[i].getLocation());
                    if(visibleTrees[i].containedRobot != null)
                    {
                        if(iDist < dtcRT)
                        {
                            iocRT = i;
                            dtcRT = iDist;
                        }
                    }
                    if(visibleTrees[i].getContainedBullets() > 0)
                    {
                        if(iDist < dtcBT)
                        {
                            iocBT = i;
                            dtcBT = iDist;
                        }
                    }
                    if(visibleTrees[i].team == rc.getTeam().opponent())
                    {
                        if(iDist < dtcET)
                        {
                            iocET = i;
                            dtcET = iDist;
                        }
                    }
                    else if(visibleTrees[i].team == Team.NEUTRAL)
                    {
                        if(iDist < dtcNT)
                        {
                            iocNT = i;
                            dtcNT = iDist;
                        }
                    }
                }

                RobotInfo[] visibleEnemies = rc.senseNearbyRobots(RobotType.TANK.sensorRadius*2,rc.getTeam().opponent());
                int maxEIndex = visibleEnemies.length;
                float maxEDist = RobotType.TANK.sensorRadius*2;
                int iocE = maxEIndex;
                float dtcE = maxEDist;

                for(int i = 0;i<visibleEnemies.length;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleEnemies[i].getLocation());
                    if(iDist < dtcE)
                    {
                        iocE = i;
                        dtcE = iDist;
                    }
                }

                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.TANK.strideRadius*2);


                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////

                for(int i = 0; i < nearbyTrees.length ; i++) {
                    if (rc.canShake(nearbyTrees[i].ID) && nearbyTrees[i].containedBullets > 0)
                    {
                        rc.shake(nearbyTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////           ATTACK          //////
                //////////////////////////////////////

                RobotInfo[] strikeableRobots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS*5,rc.getTeam().opponent());
                if(strikeableRobots.length > 0 && rc.canFireSingleShot())
                {
                    rc.fireSingleShot(rc.getLocation().directionTo(strikeableRobots[0].getLocation()));
                    moveFlag = false;
                }

                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                if(moveFlag)
                {
                    if (visibleTrees.length == 0 && visibleEnemies.length == 0)
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                    else if (iocE < maxEIndex)
                        tryMove(rc.getLocation().directionTo(visibleEnemies[iocE].getLocation()));
                    /*else if (iocRT < maxIndex)
                        tryMove(visibleTrees[iocRT].getLocation().directionTo(rc.getLocation()));
                    else if (iocET < maxIndex)
                        tryMove(visibleTrees[iocET].getLocation().directionTo(rc.getLocation()));
                    else if (iocNT < maxIndex)
                        tryMove(visibleTrees[iocNT].getLocation().directionTo(rc.getLocation()));
                    else if (iocBT < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocBT].getLocation()));
                        */
                    else
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                }
                //////////////////////////////////////
                /////           DONATE          //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////          BROADCAST        //////
                //////////////////////////////////////

                //BROADCAST IMPLEMNTATION HERE

                //////////////////////////////////////
                /////          END TURN         //////
                //////////////////////////////////////
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                boolean moveFlag = true;
                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////

                TreeInfo[] visibleTrees = rc.senseNearbyTrees(RobotType.SCOUT.sensorRadius*2);
                int maxIndex = visibleTrees.length;
                float maxDist = RobotType.SCOUT.sensorRadius*2;
                int iocRT = maxIndex;
                int iocBT = maxIndex;
                int iocET = maxIndex;
                int iocNT = maxIndex;
                float dtcRT = maxDist;
                float dtcBT = maxDist;
                float dtcET = maxDist;
                float dtcNT = maxDist;

                for(int i =0;i<maxIndex;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleTrees[i].getLocation());
                    if(visibleTrees[i].containedRobot != null)
                    {
                        if(iDist < dtcRT)
                        {
                            iocRT = i;
                            dtcRT = iDist;
                        }
                    }
                    if(visibleTrees[i].getContainedBullets() > 0)
                    {
                        if(iDist < dtcBT)
                        {
                            iocBT = i;
                            dtcBT = iDist;
                        }
                    }
                    if(visibleTrees[i].team == rc.getTeam().opponent())
                    {
                        if(iDist < dtcET)
                        {
                            iocET = i;
                            dtcET = iDist;
                        }
                    }
                    else if(visibleTrees[i].team == Team.NEUTRAL)
                    {
                        if(iDist < dtcNT)
                        {
                            iocNT = i;
                            dtcNT = iDist;
                        }
                    }
                }

                RobotInfo[] visibleEnemies = rc.senseNearbyRobots(RobotType.SCOUT.sensorRadius*2,rc.getTeam().opponent());
                int maxEIndex = visibleEnemies.length;
                float maxEDist = RobotType.SOLDIER.sensorRadius*2;
                int iocE = maxEIndex;
                float dtcE = maxEDist;

                for(int i = 0;i<visibleEnemies.length;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleEnemies[i].getLocation());
                    if(iDist < dtcE)
                    {
                        iocE = i;
                        dtcE = iDist;
                    }
                }

                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.SCOUT.strideRadius*2);


                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////

                for(int i = 0; i < nearbyTrees.length ; i++) {
                    if (rc.canShake(nearbyTrees[i].ID) && nearbyTrees[i].containedBullets > 0)
                    {
                        rc.shake(nearbyTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////           ATTACK          //////
                //////////////////////////////////////

                RobotInfo[] strikeableRobots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS,rc.getTeam().opponent());
                if(strikeableRobots.length > 0 && rc.canFireSingleShot())
                {
                    rc.fireSingleShot(rc.getLocation().directionTo(strikeableRobots[0].getLocation()));
                    moveFlag = false;
                }

                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                if(moveFlag)
                {
                    if (visibleTrees.length == 0 && visibleEnemies.length == 0)
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                    else if (iocE < maxEIndex)
                        tryMove(rc.getLocation().directionTo(visibleEnemies[iocE].getLocation()));
                    /*else if (iocRT < maxIndex)
                        tryMove(visibleTrees[iocRT].getLocation().directionTo(rc.getLocation()));
                    else if (iocET < maxIndex)
                        tryMove(visibleTrees[iocET].getLocation().directionTo(rc.getLocation()));
                    else if (iocNT < maxIndex)
                        tryMove(visibleTrees[iocNT].getLocation().directionTo(rc.getLocation()));
                    else if (iocBT < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocBT].getLocation()));
                        */
                    else
                    {
                        if(rc.getRoundNum()<2200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]),90,3);
                        else
                            tryMove(randomDirection());
                    }
                }
                //////////////////////////////////////
                /////           DONATE          //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////          BROADCAST        //////
                //////////////////////////////////////

                //BROADCAST IMPLEMNTATION HERE

                //////////////////////////////////////
                /////          END TURN         //////
                //////////////////////////////////////
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        /**
         * Things a Gardener can do
         * Sense               - close trees (for shake and attack)
         * Shake               - shakes trees if close enough and has bullets
         * Attack (chop/strike)- 1 - stop and chop down enemy/neutral tree, 2 - stop and strike if enemy within strike radius
         * Move                - move randomly
         * Donate              - end game if able, buy 1 VP if excess bulletes (excess loweres each round)
         * Broadcast           - not implemented
        **/

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                boolean moveFlag = true;
                //////////////////////////////////////
                /////           SENSE           //////
                //////////////////////////////////////
                //ioc_t   index of closest _ tree (Robot,Bullet,Enemy,Neutral)
                //dtc_t   distance to closest _ tree
                TreeInfo[] visibleTrees = rc.senseNearbyTrees(RobotType.LUMBERJACK.sensorRadius*2);
                int maxIndex = visibleTrees.length;
                float maxDist = RobotType.LUMBERJACK.sensorRadius*2;
                int iocRT = maxIndex;
                int iocBT = maxIndex;
                int iocET = maxIndex;
                int iocNT = maxIndex;
                float dtcRT = maxDist;
                float dtcBT = maxDist;
                float dtcET = maxDist;
                float dtcNT = maxDist;

                for(int i =0;i<maxIndex;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleTrees[i].getLocation());
                    if(visibleTrees[i].containedRobot != null)
                    {
                        if(iDist < dtcRT)
                        {
                            iocRT = i;
                            dtcRT = iDist;
                        }
                    }
                    if(visibleTrees[i].getContainedBullets() > 0)
                    {
                        if(iDist < dtcBT)
                        {
                            iocBT = i;
                            dtcBT = iDist;
                        }
                    }
                    if(visibleTrees[i].team == rc.getTeam().opponent())
                    {
                        if(iDist < dtcET)
                        {
                            iocET = i;
                            dtcET = iDist;
                        }
                    }
                    else if(visibleTrees[i].team == Team.NEUTRAL)
                    {
                        if(iDist < dtcNT)
                        {
                            iocNT = i;
                            dtcNT = iDist;
                        }
                    }
                }

                RobotInfo[] visibleEnemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.sensorRadius*2,rc.getTeam().opponent());
                int maxEIndex = visibleEnemies.length;
                float maxEDist = RobotType.LUMBERJACK.sensorRadius*2;
                int iocE = maxEIndex;
                float dtcE = maxEDist;

                for(int i = 0;i<visibleEnemies.length;i++)
                {
                    float iDist = rc.getLocation().distanceTo(visibleEnemies[i].getLocation());
                    if(iDist < dtcE)
                    {
                        iocE = i;
                        dtcE = iDist;
                    }
                }

                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.LUMBERJACK.strideRadius*2);


                //////////////////////////////////////
                /////           SHAKE           //////
                //////////////////////////////////////

                for(int i = 0; i < nearbyTrees.length ; i++) {
                    if (rc.canShake(nearbyTrees[i].ID) && nearbyTrees[i].containedBullets > 0)
                    {
                        rc.shake(nearbyTrees[i].ID);
                        break;
                    }
                }

                //////////////////////////////////////
                /////           ATTACK          //////
                //////////////////////////////////////

                RobotInfo[] strikeableRobots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS,rc.getTeam().opponent());
                if(strikeableRobots.length > 0)
                {
                    rc.strike();
                    moveFlag = false;
                }
                if(rc.canStrike())
                {
                    for(int i = 0; i < nearbyTrees.length ; i++)
                    {
                        if(rc.canChop(nearbyTrees[i].ID) && nearbyTrees[i].team != rc.getTeam())
                        {
                            rc.chop(nearbyTrees[i].ID);
                            moveFlag = false;
                            break;
                        }
                    }
                }

                //////////////////////////////////////
                /////            MOVE           //////
                //////////////////////////////////////
                if(moveFlag)
                {
                    if (visibleTrees.length == 0  && visibleEnemies.length == 0)
                    {
                        if(rc.getRoundNum()<200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]));
                        else
                            tryMove(randomDirection());
                    }
                    else if (iocRT < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocRT].getLocation()));
                    else if (iocBT < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocBT].getLocation()));
                    else if (iocE < maxEIndex)
                        tryMove(rc.getLocation().directionTo(visibleEnemies[iocE].getLocation()));
                    else if (iocET < maxIndex)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocET].getLocation()));
                    else if (iocNT < maxIndex && Math.random() < .01)
                        tryMove(rc.getLocation().directionTo(visibleTrees[iocNT].getLocation()));
                    else
                    {
                        if(rc.getRoundNum()<200)
                            tryMove(rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]));
                        else
                            tryMove(randomDirection());
                    }
                }
                //////////////////////////////////////
                /////          DONATE           //////
                //////////////////////////////////////

                giveDonation();

                //////////////////////////////////////
                /////          END TURN         //////
                //////////////////////////////////////
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    /**
     * If team has enough bullets to win, ends game
     * Otherwise buy 1 VP if team has excess bullets (excess = rounds remaining)
     * @throws GameActionException
     */
    static void giveDonation() throws GameActionException {
        double vpCost = 7.5 + (rc.getRoundNum())*12.5 / 3000;
        if((GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints()) * vpCost <= rc.getTeamBullets())
            rc.donate(rc.getTeamBullets());
        else if(rc.getTeamBullets() > (rc.getRoundLimit() - rc.getRoundNum())/4)
            rc.donate((float)vpCost);
    }
}
