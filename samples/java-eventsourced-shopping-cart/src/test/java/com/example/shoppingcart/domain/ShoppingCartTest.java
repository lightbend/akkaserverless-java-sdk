/* This code was initialised by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package com.example.shoppingcart.domain;

import com.example.shoppingcart.ShoppingCartApi;
import com.example.shoppingcart.domain.ShoppingCartDomain;
import com.google.protobuf.Empty;
import org.junit.Test;
import java.util.NoSuchElementException;
import com.example.shoppingcart.domain.ShoppingCartTestKit;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity.Effect;
import com.akkaserverless.javasdk.impl.effect.SecondaryEffectImpl;
import com.akkaserverless.javasdk.testkit.EventSourcedResult;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;

public class ShoppingCartTest {

    @Test
    public void addItemTest() {
        ShoppingCartTestKit testKit = new ShoppingCartTestKit(new ShoppingCart());

        ShoppingCartApi.AddLineItem commandA = ShoppingCartApi.AddLineItem.newBuilder().setProductId("idA")
                .setName("nameA").setQuantity(1).build();
        EventSourcedResult<Empty> resultA = testKit.addItem(commandA);

        ShoppingCartApi.AddLineItem commandB = ShoppingCartApi.AddLineItem.newBuilder().setProductId("idB")
                .setName("nameB").setQuantity(2).build();
        testKit.addItem(commandB);

        assertEquals(1, resultA.getAllEvents().size());
        assertEquals(2, testKit.getAllEvents().size());

        ShoppingCartDomain.ItemAdded itemAddedA = resultA.getNextEventOfType(ShoppingCartDomain.ItemAdded.class);
        assertEquals("nameA", itemAddedA.getItem().getName());
        assertThrows(NoSuchElementException.class, () ->  resultA.getNextEventOfType(ShoppingCartDomain.ItemAdded.class));
        assertEquals(Empty.getDefaultInstance(), resultA.getReply());

        ShoppingCartDomain.LineItem expectedLineItemA = ShoppingCartDomain.LineItem.newBuilder().setProductId("idA")
                .setName("nameA").setQuantity(1).build();        
        ShoppingCartDomain.LineItem expectedLineItemB = ShoppingCartDomain.LineItem.newBuilder().setProductId("idB")
                .setName("nameB").setQuantity(2).build();
        ShoppingCartDomain.Cart expectedState = ShoppingCartDomain.Cart.newBuilder()
                .addItems(expectedLineItemA)
                .addItems(expectedLineItemB)
                .build();
        assertEquals(expectedState, testKit.getState());
    }
}
