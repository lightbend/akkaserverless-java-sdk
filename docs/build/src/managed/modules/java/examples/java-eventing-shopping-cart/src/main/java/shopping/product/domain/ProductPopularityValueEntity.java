/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package shopping.product.domain;

import com.akkaserverless.javasdk.valueentity.ValueEntityContext;
import com.google.protobuf.Empty;
import shopping.product.api.ProductPopularityApi;

import java.util.Optional;

/** A value entity. */
public class ProductPopularityValueEntity extends AbstractProductPopularityValueEntity {
  @SuppressWarnings("unused")
  private final String entityId;

  public ProductPopularityValueEntity(ValueEntityContext context) {
    this.entityId = context.entityId();
  }

  /**
   * Create a new ProductPopularity build with popularity score set to 0 and for the current
   * productId
   */
  @Override
  public ProductPopularityDomain.Popularity emptyState() {
    return ProductPopularityDomain.Popularity.newBuilder().setProductId(entityId).setScore(0).build();
  }

  @Override
  public Effect<Empty> increase(ProductPopularityDomain.Popularity currentState, ProductPopularityApi.IncreasePopularity command) {
    ProductPopularityDomain.Popularity.Builder builder = currentState.toBuilder();
    int newScore = builder.getScore() + command.getQuantity();
    builder.setProductId(command.getProductId());
    ProductPopularityDomain.Popularity updated = builder.setScore(newScore).build();
    return effects().updateState(updated).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> decrease(ProductPopularityDomain.Popularity currentState, ProductPopularityApi.DecreasePopularity command) {
    ProductPopularityDomain.Popularity.Builder builder = currentState.toBuilder();
    int newScore = builder.getScore() - command.getQuantity();
    builder.setScore(newScore);
    return effects().updateState(builder.build()).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<ProductPopularityApi.Popularity> getPopularity(ProductPopularityDomain.Popularity currentState, ProductPopularityApi.GetProductPopularity command) {
    return effects().reply(convert(currentState));
  }

  private ProductPopularityApi.Popularity convert(ProductPopularityDomain.Popularity popularity) {
    return ProductPopularityApi.Popularity.newBuilder()
        .setProductId(entityId)
        .setScore(popularity.getScore())
        .build();
  }

}