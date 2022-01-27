/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015-2017 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.almasb.fxglgames.pong;

import com.almasb.fxgl.animation.Interpolators;
import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.core.math.FXGLMath;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.net.*;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.physics.HitBox;
import com.almasb.fxgl.ui.UI;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.almasb.fxgl.dsl.FXGL.*;
import static com.almasb.fxglgames.pong.NetworkMessages.*;

/**
 * A simple clone of Pong.
 * Sounds from https://freesound.org/people/NoiseCollector/sounds/4391/ under CC BY 3.0.
 *
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
public class PongApp extends GameApplication implements MessageHandler<String> {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Pong");
        settings.setVersion("1.0");
        settings.setFontUI("pong.ttf");
        settings.setApplicationMode(ApplicationMode.DEBUG);
    }

    private Entity player1;
    private Entity player2;
    int numPlayers = 0;
    int numBalls = 0;
    int updatedNumBalls = 0;
    private Entity ball;
    private Entity plusOnePowerUp;
    private Entity minusOnePowerUp;
    private Entity halfPowerUp;
    private Entity doublePowerUp;

    private int powerUpX;
    private int powerUpY;
    private int powerUpNum;

    private LinkedList<Entity> balls = new LinkedList<Entity>();
    private BatComponent player1Bat;
    private BatComponent player2Bat;
    private PowerUpSpawner powerUpSpawner;
    private PowerUpComponent halfComponent;
    private PowerUpComponent doubleComponent;
    private Server<String> server;

    @Override
    protected void initInput() {
        getInput().addAction(new UserAction("Up1") {
            @Override
            protected void onAction() {
                player1Bat.up();
            }

            @Override
            protected void onActionEnd() {
                player1Bat.stop();
            }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Down1") {
            @Override
            protected void onAction() {
                player1Bat.down();
            }

            @Override
            protected void onActionEnd() {
                player1Bat.stop();
            }
        }, KeyCode.S);

        getInput().addAction(new UserAction("Up2") {
            @Override
            protected void onAction() {
                player2Bat.up();
            }

            @Override
            protected void onActionEnd() {
                player2Bat.stop();
            }
        }, KeyCode.I);

        getInput().addAction(new UserAction("Down2") {
            @Override
            protected void onAction() {
                player2Bat.down();
            }

            @Override
            protected void onActionEnd() {
                player2Bat.stop();
            }
        }, KeyCode.K);
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("player1score", 0);
        vars.put("player2score", 0);
    }

    @Override
    protected void initGame() {
        Writers.INSTANCE.addTCPWriter(String.class, outputStream -> new MessageWriterS(outputStream));
        Readers.INSTANCE.addTCPReader(String.class, in -> new MessageReaderS(in));

        server = getNetService().newTCPServer(55555, new ServerConfig<>(String.class));

        server.setOnConnected(connection -> {
            connection.addMessageHandlerFX(this);
            numPlayers++;
            server.broadcast("PLAYERNUM," + numPlayers);
        });

        getGameWorld().addEntityFactory(new PongFactory());
        getGameScene().setBackgroundColor(Color.rgb(0, 0, 5));

        initScreenBounds();
        initGameObjects();

        var t = new Thread(server.startTask()::run);
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void initPhysics() {
        getPhysicsWorld().setGravity(0, 0);

        getPhysicsWorld().addCollisionHandler(new CollisionHandler(EntityType.BALL, EntityType.WALL) {
            @Override
            protected void onHitBoxTrigger(Entity a, Entity b, HitBox boxA, HitBox boxB) {
                if (boxB.getName().equals("LEFT")) {
                    inc("player2score", +1);

                    server.broadcast("SCORES," + geti("player1score") + "," + geti("player2score"));

                    server.broadcast(HIT_WALL_LEFT);
                } else if (boxB.getName().equals("RIGHT")) {
                    inc("player1score", +1);

                    server.broadcast("SCORES," + geti("player1score") + "," + geti("player2score"));

                    server.broadcast(HIT_WALL_RIGHT);
                } else if (boxB.getName().equals("TOP")) {
                    server.broadcast(HIT_WALL_UP);
                } else if (boxB.getName().equals("BOT")) {
                    server.broadcast(HIT_WALL_DOWN);
                }

                getGameScene().getViewport().shakeTranslational(5);
            }
        });

        CollisionHandler ballBatHandler = new CollisionHandler(EntityType.BALL, EntityType.PLAYER_BAT) {
            @Override
            protected void onCollisionBegin(Entity a, Entity bat) {
                playHitAnimation(bat);
                server.broadcast(bat == player1 ? BALL_HIT_BAT1 : BALL_HIT_BAT2);
            }
        };
        CollisionHandler ballPowerUpHalfHandler = new CollisionHandler(EntityType.BALL, EntityType.HALF) {
            @Override
            protected void onCollisionBegin(Entity a, Entity powerUp) {
                updatedNumBalls = halfComponent.calcNewNumBalls(EntityType.HALF, numBalls);
                if(updatedNumBalls <= 1){ updatedNumBalls = 1;}
                while(numBalls > updatedNumBalls){
                    despawnBall();
                }
                powerUp.removeFromWorld();
                powerUpX = 0;
                powerUpY = 0;
                powerUpNum = 4;
                sendPowerUpData(0,0,4);
                powerUpSpawner.startTimer();
            }
        };
        CollisionHandler ballPowerUpDoubleHandler = new CollisionHandler(EntityType.BALL, EntityType.DOUBLE) {
            @Override
            protected void onCollisionBegin(Entity a, Entity powerUp) {
                updatedNumBalls = doubleComponent.calcNewNumBalls(EntityType.DOUBLE, numBalls);
                if(updatedNumBalls > 10){ updatedNumBalls = 10;}
                while(numBalls < updatedNumBalls){
                    spawnBall();
                }
                powerUp.removeFromWorld();
                powerUpX = 0;
                powerUpY = 0;
                powerUpNum = 4;
                sendPowerUpData(0,0,4);
                powerUpSpawner.startTimer();
            }
        };
        CollisionHandler ballPowerUpPlusOneHandler = new CollisionHandler(EntityType.BALL, EntityType.PLUS_ONE) {
            @Override
            protected void onCollisionBegin(Entity a, Entity powerUp) {
                if(numBalls < 10) {
                    spawnBall();
                }
                powerUp.removeFromWorld();
                powerUpX = 0;
                powerUpY = 0;
                powerUpNum = 4;
                sendPowerUpData(0,0,4);
                powerUpSpawner.startTimer();
            }
        };
        CollisionHandler ballPowerUpMinusOneHandler = new CollisionHandler(EntityType.BALL, EntityType.MINUS_ONE) {
            @Override
            protected void onCollisionBegin(Entity a, Entity powerUp) {
                powerUp.removeFromWorld();
                if(numBalls > 1) {
                    despawnBall();
                }
                powerUpX = 0;
                powerUpY = 0;
                powerUpNum = 4;
                sendPowerUpData(0,0,4);
                powerUpSpawner.startTimer();

            }
        };

        getPhysicsWorld().addCollisionHandler(ballBatHandler);
        getPhysicsWorld().addCollisionHandler(ballBatHandler.copyFor(EntityType.BALL, EntityType.ENEMY_BAT));
        getPhysicsWorld().addCollisionHandler(ballPowerUpHalfHandler);
        getPhysicsWorld().addCollisionHandler(ballPowerUpDoubleHandler);
        getPhysicsWorld().addCollisionHandler(ballPowerUpPlusOneHandler);
        getPhysicsWorld().addCollisionHandler(ballPowerUpMinusOneHandler);
    }
    protected void sendPowerUpData(int powerUpX,int powerUpY, int powerUpNum){
        var message = "POWER_UP," + powerUpX + "," + powerUpY + "," + powerUpNum;
        server.broadcast(message);
    }

    @Override
    protected void initUI() {
        MainUIController controller = new MainUIController();
        UI ui = getAssetLoader().loadUI("main.fxml", controller);

        controller.getLabelScorePlayer().textProperty().bind(getip("player1score").asString());
        controller.getLabelScoreEnemy().textProperty().bind(getip("player2score").asString());
        getGameScene().addUI(ui);
    }

    @Override
    protected void onUpdate(double tpf) {
        if(powerUpSpawner.isPowerUpSpawned){
            int randNum = randomNumGen(3,0);
            EntityType powerUpET = null;
            if(randNum == 0){
                powerUpET = EntityType.MINUS_ONE;
                powerUpNum = 0;
            } else if (randNum == 1){
                powerUpET = EntityType.PLUS_ONE;
                powerUpNum = 1;
            } else if (randNum == 2){
                powerUpET = EntityType.HALF;
                powerUpNum = 2;

            } else {
                powerUpET = EntityType.DOUBLE;
                powerUpNum = 3;
            }
            powerUpX = randomNumGen(getAppWidth() - 45, 0);
            powerUpY = randomNumGen(getAppHeight() - 45, 0);
            spawnPowerUp(powerUpET);
            sendPowerUpData(powerUpX, powerUpY, powerUpNum);
            powerUpSpawner.isPowerUpSpawned = false;
        }

        if (!server.getConnections().isEmpty()) {
            var message = "GAME_DATA," + numBalls + "," + player1.getY() + "," + player2.getY();
            var ballMessage = "";
            for(int it = 0 ;it < balls.size(); it++){
                ballMessage = ballMessage + "," + balls.get(it).getX() + "," + balls.get(it).getY();
            }
            message = message + ballMessage;
            server.broadcast(message);
        }

    }

    private void initScreenBounds() {
        Entity walls = entityBuilder()
                .type(EntityType.WALL)
                .collidable()
                .buildScreenBounds(150);

        getGameWorld().addEntity(walls);
    }

    private void initGameObjects() {
        spawnBall();
        player1 = spawn("bat", new SpawnData(getAppWidth() / 4, getAppHeight() / 2 - 30).put("isPlayer", true));
        player2 = spawn("bat", new SpawnData(3 * getAppWidth() / 4 - 20, getAppHeight() / 2 - 30).put("isPlayer", false));
        powerUpSpawner = new PowerUpSpawner();
        powerUpSpawner.startTimer();
        powerUpX = 0;
        powerUpY = 0;
        powerUpNum = 4;
        player1Bat = player1.getComponent(BatComponent.class);
        player2Bat = player2.getComponent(BatComponent.class);
    }
    private void spawnPowerUp(EntityType ET){
        if(ET == EntityType.MINUS_ONE){
            minusOnePowerUp = spawn("powerUp", new SpawnData(powerUpX,powerUpY).put("powerUpType", "MINUS_ONE"));
        } else if(ET == EntityType.PLUS_ONE){
            plusOnePowerUp = spawn("powerUp", new SpawnData(powerUpX,powerUpY).put("powerUpType", "PLUS_ONE"));
        } else if(ET == EntityType.HALF){
            halfPowerUp = spawn("powerUp", new SpawnData(powerUpX,powerUpY).put("powerUpType", "HALF"));
            halfComponent = halfPowerUp.getComponent(PowerUpComponent.class);
        } else {
            doublePowerUp = spawn("powerUp", new SpawnData(powerUpX,powerUpY).put("powerUpType", "DOUBLE"));
            doubleComponent = doublePowerUp.getComponent(PowerUpComponent.class);
        }
    }
    private int randomNumGen(int max, int min){
        return (int) Math.floor(Math.random() * (max - min + 1) + min);
    }
    private void spawnBall(){
        ball = spawn("ball", randomNumGen(getAppWidth() - 5, 0),randomNumGen(getAppHeight() - 5, 0));
        balls.add(ball);
        numBalls++;
    }
    private void despawnBall(){
        balls.getLast().removeFromWorld();
        balls.removeLast();
        numBalls--;
    }

    private void playHitAnimation(Entity bat) {
        animationBuilder()
                .autoReverse(true)
                .duration(Duration.seconds(0.5))
                .interpolator(Interpolators.BOUNCE.EASE_OUT())
                .rotate(bat)
                .from(FXGLMath.random(-25, 25))
                .to(0)
                .buildAndPlay();
    }

    @Override
    public void onReceive(Connection<String> connection, String message) {
        var tokens = message.split(",");
        Arrays.stream(tokens).skip(1).forEach(key -> {
            if (key.endsWith("_DOWN")) {
                getInput().mockKeyPress(KeyCode.valueOf(key.substring(0, 1)));
            } else if (key.endsWith("_UP")) {
                getInput().mockKeyRelease(KeyCode.valueOf(key.substring(0, 1)));
            } else if (key.endsWith("_POWERUP")){
                sendPowerUpData(powerUpX,powerUpY,powerUpNum);
            }
        });


    }

    static class MessageWriterS implements TCPMessageWriter<String> {

        private OutputStream os;
        private PrintWriter out;

        MessageWriterS(OutputStream os) {
            this.os = os;
            out = new PrintWriter(os, true);
        }

        @Override
        public void write(String s) throws Exception {
            out.print(s.toCharArray());
            out.flush();
        }
    }

    static class MessageReaderS implements TCPMessageReader<String> {

        private BlockingQueue<String> messages = new ArrayBlockingQueue<>(50);

        private InputStreamReader in;

        MessageReaderS(InputStream is) {
            in =  new InputStreamReader(is);

            var t = new Thread(() -> {
                try {

                    char[] buf = new char[36];

                    int len;

                    while ((len = in.read(buf)) > 0) {
                        var message = new String(Arrays.copyOf(buf, len));

                        System.out.println("Recv message: " + message);

                        messages.put(message);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            t.setDaemon(true);
            t.start();
        }

        @Override
        public String read() throws Exception {
            return messages.take();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
