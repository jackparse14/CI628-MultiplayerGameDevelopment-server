package com.almasb.fxglgames.pong;

import com.almasb.fxgl.entity.component.Component;

public class PowerUpComponent extends Component {

    private int randomNumGen(int min, int max) {
        return ((int) Math.floor(Math.random() * (max - min + 1) + min));
    }
    public int calcNewNumBalls(EntityType ET, int numBalls){
        int newNumBalls = 0;
        if(ET == EntityType.DOUBLE){
            newNumBalls = numBalls*2;
        } else {
            newNumBalls = (int)Math.ceil(numBalls/2);
        }
        return newNumBalls;
    }
}
