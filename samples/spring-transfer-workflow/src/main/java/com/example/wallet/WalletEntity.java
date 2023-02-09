package com.example.wallet;

import kalix.javasdk.valueentity.ValueEntity;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

// tag::wallet[]
@EntityKey("id")
@EntityType("wallet")
@RequestMapping("/wallet/{id}")
public class WalletEntity extends ValueEntity<WalletEntity.Wallet> {

  public record Wallet(String id, int balance) {
    public Wallet withdraw(int amount) {
      return new Wallet(id, balance - amount);
    }
    public Wallet deposit(int amount) {
      return new Wallet(id, balance + amount);
    }
  }

  public record Balance(int value) {
  }

  // end::wallet[]

  private static final Logger logger = LoggerFactory.getLogger(WalletEntity.class);
  // tag::wallet[]
  @PostMapping("/create/{initBalance}") // <1>
  public Effect<String> create(@PathVariable String id, @PathVariable int initBalance) {
    return effects().updateState(new Wallet(id, initBalance)).thenReply("Ok");
  }

  @PatchMapping("/withdraw/{amount}") // <2>
  public Effect<String> withdraw(@PathVariable int amount) {
    var newBalance = currentState().balance() - amount;
    if (newBalance < 0) {
      return effects().error("Insufficient balance");
    } else {
      Wallet updatedWallet = currentState().withdraw(amount);
      // end::wallet[]
      logger.info("Withdraw walletId: [{}] amount -{} balance after {}", currentState().id(), amount, updatedWallet.balance());
      // tag::wallet[]
      return effects().updateState(updatedWallet).thenReply("Ok");
    }
  }

  @PatchMapping("/deposit/{amount}") // <3>
  public Effect<String> deposit(@PathVariable int amount) {
    Wallet updatedWallet = currentState().deposit(amount);
    // end::wallet[]
    logger.info("Deposit walletId: [{}] amount +{} balance after {}", currentState().id(), amount, updatedWallet.balance());
    // tag::wallet[]
    return effects().updateState(updatedWallet).thenReply("Ok");
  }

  @GetMapping // <4>
  public Effect<Balance> get() {
    return effects().reply(new Balance(currentState().balance()));
  }
}
// end::wallet[]
