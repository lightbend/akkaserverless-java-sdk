package com.example.actions;

import com.example.CounterEvent;
import com.example.CounterEvent.ValueIncreased;
import com.example.CounterEvent.ValueMultiplied;
import com.example.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// tag::class[]
import kalix.javasdk.action.Action;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = Counter.class, ignoreUnknown = true) // <1>
public class SubscribeTypeLevelAction extends Action {

    private Logger logger = LoggerFactory.getLogger(SubscribeTypeLevelAction.class);

    public Action.Effect<Confirmed> onIncrease(ValueIncreased event){ // <2>
        logger.info("Received increased event: " + event.toString());
        return effects().reply(Confirmed.instance); // <3>
    }
}
// end::class[]