package com.example.transfer;

import com.example.transfer.TransferState.Transfer;
import kalix.javasdk.workflow.Workflow;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.example.transfer.TransferState.TransferStatus.COMPLETED;
import static com.example.transfer.TransferState.TransferStatus.WITHDRAW_SUCCEED;

// tag::class[]
@EntityType("transfer") // <2>
@EntityKey("transferId") // <3>
@RequestMapping("/transfer/{transferId}") // <4>
public class TransferWorkflow extends Workflow<TransferState> { // <1>
  // end::class[]

  // tag::start[]
  public record Withdraw(String from, int amount) { // <4>
  }

  // end::start[]

  // tag::definition[]
  public record Deposit(String to, int amount) {
  }

  // end::definition[]

  private static final Logger logger = LoggerFactory.getLogger(TransferWorkflow.class);

  final private KalixClient kalixClient;

  public TransferWorkflow(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  // tag::definition[]
  @Override
  public WorkflowDef<TransferState> definition() {
    Step withdraw =
      step("withdraw") // <1>
        .call(Withdraw.class, cmd -> {
          String withdrawUri = "/wallet/" + cmd.from() + "/withdraw/" + cmd.amount();
          return kalixClient.patch(withdrawUri, String.class);
        }) // <2>
        .andThen(String.class, __ -> {
          Deposit depositInput = new Deposit(currentState().transfer().to(), currentState().transfer().amount());
          return effects()
            .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
            .transitionTo("deposit", depositInput); // <3>
        });

    Step deposit =
      step("deposit") // <1>
        .call(Deposit.class, cmd -> {
          String depositUri = "/wallet/" + cmd.to() + "/deposit/" + cmd.amount();
          return kalixClient.patch(depositUri, String.class);
        }) // <4>
        .andThen(String.class, __ -> {
          return effects()
            .updateState(currentState().withStatus(COMPLETED))
            .end(); // <5>
        });

    return workflow() // <6>
      .addStep(withdraw)
      .addStep(deposit);
  }
  // end::definition[]

  // tag::start[]
  @PutMapping
  public Effect<Message> startTransfer(@RequestBody Transfer transfer) {
    if (transfer.amount() <= 0) {
      return effects().error("transfer amount should be greater than zero"); // <1>
    } else if (currentState() != null) {
      return effects().error("transfer already started"); // <2>
    } else {

      TransferState initialState = new TransferState(transfer); // <3>

      Withdraw withdrawInput = new Withdraw(transfer.from(), transfer.amount());

      return effects()
        .updateState(initialState) // <4>
        .transitionTo("withdraw", withdrawInput) // <5>
        .thenReply(new Message("transfer started")); // <6>
    }
  }
  // end::start[]

  // tag::get-transfer[]
  @GetMapping // <1>
  public Effect<TransferState> getTransferState() {
    if (currentState() == null) {
      return effects().error("transfer not started");
    } else {
      return effects().reply(currentState()); // <2>
    }
  }
  // end::get-transfer[]
}
