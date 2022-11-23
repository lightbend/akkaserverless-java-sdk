/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kalix.springsdk.impl

import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import kalix.JwtMethodOptions.JwtMethodMode
import kalix.springsdk.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionView
import kalix.springsdk.testmodels.subscriptions.PubSubTestModels.SubscribeOnTypeToEventSourcedEvents
import kalix.springsdk.testmodels.view.ViewTestModels.IllDefineUserByEmailWithStreamUpdates
import kalix.springsdk.testmodels.view.ViewTestModels.SubscribeToEventSourcedEvents
import kalix.springsdk.testmodels.view.ViewTestModels.SubscribeToEventSourcedEventsWithMethodWithState
import kalix.springsdk.testmodels.view.ViewTestModels.TransformedUserView
import kalix.springsdk.testmodels.view.ViewTestModels.TransformedUserViewUsingState
import kalix.springsdk.testmodels.view.ViewTestModels.TransformedUserViewWithDeletes
import kalix.springsdk.testmodels.view.ViewTestModels.TransformedUserViewWithJWT
import kalix.springsdk.testmodels.view.ViewTestModels.UserByEmailWithCollectionReturn
import kalix.springsdk.testmodels.view.ViewTestModels.UserByEmailWithGet
import kalix.springsdk.testmodels.view.ViewTestModels.UserByEmailWithPost
import kalix.springsdk.testmodels.view.ViewTestModels.UserByEmailWithPostRequestBodyOnly
import kalix.springsdk.testmodels.view.ViewTestModels.UserByEmailWithStreamUpdates
import kalix.springsdk.testmodels.view.ViewTestModels.UserByNameEmailWithPost
import kalix.springsdk.testmodels.view.ViewTestModels.UserByNameStreamed
import kalix.springsdk.testmodels.view.ViewTestModels.ViewDuplicatedHandleDeletesAnnotations
import kalix.springsdk.testmodels.view.ViewTestModels.ViewDuplicatedSubscriptions
import kalix.springsdk.testmodels.view.ViewTestModels.ViewHandleDeletesWithParam
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithHandleDeletesFalseOnMethodLevel
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithMethodLevelAcl
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithNoQuery
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithServiceLevelAcl
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithSubscriptionsInMixedLevels
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithSubscriptionsInMixedLevelsHandleDelete
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithTwoQueries
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithoutEmptyTableAnnotation
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithoutSubscriptionButWithHandleDelete
import kalix.springsdk.testmodels.view.ViewTestModels.ViewWithoutTableAnnotation
import org.scalatest.wordspec.AnyWordSpec

class ViewDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "View descriptor factory" should {

    "generate ACL annotations at service level" in {
      assertDescriptor[ViewWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[ViewWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "GetEmployeeByEmail")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate query with stream updates enabled" in {
      assertDescriptor[UserByEmailWithStreamUpdates] { desc =>
        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val streamUpdates = queryMethodOptions.getView.getQuery.getStreamUpdates
        streamUpdates shouldBe true
      }
    }

    "generate query with collection return type" in {
      assertDescriptor[UserByEmailWithCollectionReturn] { desc =>
        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * AS users FROM users_view WHERE name = :name"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "UserCollection"

        val streamUpdates = queryMethodOptions.getView.getQuery.getStreamUpdates
        streamUpdates shouldBe false
      }
    }

    "fail when using stream updates in unary methods" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[IllDefineUserByEmailWithStreamUpdates]).failIfInvalid
      }.getMessage should include("@Query.streamUpdates can only be enabled in stream methods returning Flux")
    }
  }

  "View descriptor factory (for Value Entity)" should {

    "not allow View without Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutTableAnnotation]).failIfInvalid
      }.getMessage should include("A View should be annotated with @Table.")
    }

    "not allow View with empty Table name" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutEmptyTableAnnotation]).failIfInvalid
      }.getMessage should include("@Table name is empty, must be a non-empty string.")
    }

    "not allow @Subscribe annotations in mixed levels" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithSubscriptionsInMixedLevels]).failIfInvalid
      }.getMessage should include("You cannot use @Subscribe.ValueEntity annotation in both methods and class.")
    }

    "not allow method level handle deletes with type level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithSubscriptionsInMixedLevelsHandleDelete]).failIfInvalid
      }.getMessage should include("You cannot use @Subscribe.ValueEntity annotation in both methods and class.")
    }

    "not allow method level handle deletes without method level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutSubscriptionButWithHandleDelete]).failIfInvalid
      }.getMessage should include("Method annotated with handleDeletes=true has no matching update method.")
    }

    "not allow duplicated handle deletes methods" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedHandleDeletesAnnotations]).failIfInvalid
      }.getMessage should include(
        "Multiple methods annotated with @Subscription.ValueEntity(handleDeletes=true) is not allowed.")
    }

    "not allow handle deletes method with param" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewHandleDeletesWithParam]).failIfInvalid
      }.getMessage should include(
        "Method annotated with '@Subscribe.ValueEntity' and handleDeletes=true must not have parameters.")
    }

    "not allow handle deletes false on method level" in {
      // on method level only true is acceptable
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithHandleDeletesFalseOnMethodLevel]).failIfInvalid
      }.getMessage should include("Subscription method must have one parameter, unless it's marked as handleDeletes.")
    }

    "not allow duplicated subscriptions methods" in {
      // it should be annotated either on type or on method level
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedSubscriptions]).failIfInvalid
      }.getMessage should include("Duplicated update methods for ValueEntity subscription.")
    }

    "generate proto for a View using POST request with explicit update method" in {
      assertDescriptor[TransformedUserView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        val handleDeletes = methodOptions.getEventing.getIn.getHandleDeletes
        entityType shouldBe "user"
        handleDeletes shouldBe false

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/by-email"
      }
    }

    "generate proto for a View with delete handler" in {
      assertDescriptor[TransformedUserViewWithDeletes] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val in = methodOptions.getEventing.getIn
        in.getValueEntity shouldBe "user"
        in.getHandleDeletes shouldBe false

        val deleteMethodOptions = this.findKalixMethodOptions(desc, "OnDelete")
        val deleteIn = deleteMethodOptions.getEventing.getIn
        deleteIn.getValueEntity shouldBe "user"
        deleteIn.getHandleDeletes shouldBe true
      }
    }

    "generate proto for a View using POST request with explicit update method and JWT Kalix annotations" in {
      assertDescriptor[TransformedUserViewWithJWT] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/by-email"

        val method = desc.commandHandlers("GetUser")
        val jwtOption = findKalixMethodOptions(desc, method.grpcMethodName).getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN
        jwtOption.getSign(0) shouldBe JwtMethodMode.MESSAGE
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)
      }
    }

    "generate proto for a View using POST request with explicit update method that also receives the current state" in {
      assertDescriptor[TransformedUserViewUsingState] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/by-email"

        val javaMethod = desc.commandHandlers("OnChange").methodInvokers.values.head
        javaMethod.parameterExtractors.length shouldBe 1
      }
    }

    "generate proto for a View using POST request" in {
      assertDescriptor[UserByEmailWithPost] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false
        // check json input schema:  ByEmail
        methodOptions.getView.getJsonSchema.getOutput shouldBe "User"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("User")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/by-email"
      }
    }

    "generate proto for a View using POST request with path param " in {
      assertDescriptor[UserByNameEmailWithPost] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        methodOptions.getEventing.getIn.getValueEntity shouldBe "user"
        methodOptions.getEventing.getIn.getHandleDeletes shouldBe false

        val deleteMethodOptions = this.findKalixMethodOptions(desc, "OnDelete")
        deleteMethodOptions.getEventing.getIn.getValueEntity shouldBe "user"
        deleteMethodOptions.getEventing.getIn.getHandleDeletes shouldBe true

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false
        methodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        // check json input schema:  ByEmail

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        desc.fileDescriptor.findMessageTypeByName("User") should not be null
        desc.fileDescriptor.findMessageTypeByName("ByEmail") should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/{name}/by-email"
      }
    }

    "generate proto for a View using GET request with path param" in {
      assertDescriptor[UserByEmailWithGet] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false
        methodOptions.getView.getJsonSchema.getOutput shouldBe "User"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe ""

        val tableMessageDescriptor =
          desc.fileDescriptor.findMessageTypeByName("User")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getGet shouldBe "/users/{email}"
      }
    }

    "generate proto for a View using POST request with only request body" in {
      assertDescriptor[UserByEmailWithPostRequestBodyOnly] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false
        // check json input schema:  ByEmail
        methodOptions.getView.getJsonSchema.getOutput shouldBe "User"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        // based on the body parameter type class name
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor =
          desc.fileDescriptor.findMessageTypeByName("User")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/users/by-email"
      }
    }

    "generate proto for a View using GET request with path param and returning stream" in {
      assertDescriptor[UserByNameStreamed] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false
        methodOptions.getView.getJsonSchema.getOutput shouldBe "User"

        val methodDescriptor = desc.serviceDescriptor.findMethodByName("GetUser")
        methodDescriptor.isServerStreaming shouldBe true

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users_view WHERE name = :name"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe ""

        val tableMessageDescriptor =
          desc.fileDescriptor.findMessageTypeByName("User")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getGet shouldBe "/users/{name}"

      }
    }

    "fail if no query method found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithNoQuery]).failIfInvalid
      }
    }

    "fail if more than one query method is found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithTwoQueries]).failIfInvalid
      }
    }
  }

  "View descriptor factory (for Event Sourced Entity)" should {

    "generate proto for a View using POST request" in {
      assertDescriptor[SubscribeToEventSourcedEvents] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnEvent")
        val entityType = methodOptions.getEventing.getIn.getEventSourcedEntity
        entityType shouldBe "employee"

        methodOptions.getView.getUpdate.getTable shouldBe "employees_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe ""

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/employees/by-email/{email}"
      }
    }

    "generate proto for a View using POST request with subscription method accepting state" in {
      assertDescriptor[SubscribeToEventSourcedEventsWithMethodWithState] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnEvent")
        val entityType = methodOptions.getEventing.getIn.getEventSourcedEntity
        entityType shouldBe "employee"

        methodOptions.getView.getUpdate.getTable shouldBe "employees_view"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees_view WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe ""

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/employees/by-email/{email}"
      }
    }

    "generate proto for a View with multiple methods to handle different events" in {
      assertDescriptor[SubscribeOnTypeToEventSourcedEvents] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)
        val eveningIn = serviceOptions.getEventing.getIn
        eveningIn.getEventSourcedEntity shouldBe "employee"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        methodOptions.getEventing.getIn.getIgnore shouldBe false // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
      }
    }

    "generate mappings for service to service subscription " in {
      assertDescriptor[EventStreamSubscriptionView] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)
        val eventingInDirect = serviceOptions.getEventing.getIn.getDirect
        eventingInDirect.getService shouldBe "employee_service"
        eventingInDirect.getEventStreamId shouldBe "employee_events"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee_events")

        methodOptions.hasEventing shouldBe false
        methodOptions.getView.getUpdate.getTable shouldBe "employee_table"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

      }
    }
  }

}
