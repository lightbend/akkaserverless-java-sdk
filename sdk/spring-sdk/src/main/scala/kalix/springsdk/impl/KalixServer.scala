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

import scala.jdk.CollectionConverters.CollectionHasAsScala
import com.typesafe.config.Config

import kalix.javasdk.Kalix
import kalix.javasdk.action.Action
import kalix.javasdk.action.ActionCreationContext
import kalix.javasdk.action.ActionProvider
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.valueentity.ValueEntityContext
import kalix.javasdk.valueentity.ValueEntityProvider
import kalix.javasdk.view.View
import kalix.javasdk.view.ViewCreationContext
import kalix.javasdk.view.ViewProvider
import kalix.springsdk.SpringSdkBuildInfo
import kalix.springsdk.action.ReflectiveActionProvider
import kalix.springsdk.impl.KalixServer.ActionCreationContextFactoryBean
import kalix.springsdk.impl.KalixServer.KalixComponentProvider
import kalix.springsdk.impl.KalixServer.ValueEntityContextFactoryBean
import kalix.springsdk.impl.KalixServer.ViewCreationContextFactoryBean
import kalix.springsdk.valueentity.ReflectiveValueEntityProvider
import kalix.springsdk.view.ReflectiveViewProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.`type`.classreading.MetadataReader
import org.springframework.core.`type`.classreading.MetadataReaderFactory
import org.springframework.core.`type`.filter.TypeFilter

object KalixServer {

  /**
   * Kalix components are not Spring components. They should not be wired into other components and they should not be
   * freely available for users to access.
   *
   * Therefore, we should block the usage of any Spring stereotype annotations. As a consequence, they won't be
   * available in the app's ApplicationContext and we need to scan the classpath ourselves in order to register them.
   *
   * This class will do exactly this. It find them and return tweaked BeanDefinitions (eg :prototype scope and autowired
   * by constructor)
   */
  class KalixComponentProvider extends ClassPathScanningCandidateComponentProvider {

    object KalixComponentTypeFilter extends TypeFilter {
      // TODO: missing EventSourced and Replicated Entities
      val kalixComponents =
        classOf[Action].getName ::
        classOf[ValueEntity[_]].getName ::
        classOf[View[_]].getName ::
        Nil

      override def `match`(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory): Boolean = {
        kalixComponents.contains(metadataReader.getClassMetadata.getSuperClassName)
      }
    }

    addIncludeFilter(KalixComponentTypeFilter)

    def findKalixComponents(packageToScan: String): Seq[BeanDefinition] = {
      this
        .findCandidateComponents(packageToScan)
        .asScala
        .map { bean =>
          // by default, the provider set them all as singletons,
          // we need to make them all a prototype
          bean.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)

          // making it only wireable by constructor will simplify our lives
          // we can review it later, if needed
          bean.asInstanceOf[AbstractBeanDefinition].setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR)
          bean

        }
        .toSeq
    }
  }

  class ActionCreationContextFactoryBean(loco: ThreadLocal[ActionCreationContext])
      extends FactoryBean[ActionCreationContext] {

    // ActionCreationContext is a singleton, so strictly speaking this could return 'true'
    // However, we still need the ThreadLocal hack to let Spring have access to it.
    // and we don't want to give it direct access to it, because the impl is private (and better keep it so).
    // because the testkit uses another ActionCreationContext impl. Therefore we want it to be defined at runtime.
    override def isSingleton: Boolean = false

    override def getObject: ActionCreationContext = loco.get()
    override def getObjectType: Class[_] = classOf[ActionCreationContext]
  }

  class ValueEntityContextFactoryBean(loco: ThreadLocal[ValueEntityContext]) extends FactoryBean[ValueEntityContext] {
    override def isSingleton: Boolean = false // never!!
    override def getObject: ValueEntityContext = loco.get()
    override def getObjectType: Class[_] = classOf[ValueEntityContext]
  }

  class ViewCreationContextFactoryBean(loco: ThreadLocal[ViewCreationContext])
      extends FactoryBean[ViewCreationContext] {
    override def isSingleton: Boolean = false // never!!
    override def getObject: ViewCreationContext = loco.get()
    override def getObjectType: Class[_] = classOf[ViewCreationContext]
  }
}

class KalixServer(applicationContext: ApplicationContext, config: Config) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val kalix = (new Kalix).withSdkName(SpringSdkBuildInfo.name)

  private val threadLocalActionContext = new ThreadLocal[ActionCreationContext]
  private val threadLocalValueEntityContext = new ThreadLocal[ValueEntityContext]
  private val threadLocalViewContext = new ThreadLocal[ViewCreationContext]

  private val kalixBeanFactory = new DefaultListableBeanFactory(applicationContext)

  // the FactoryBeans below will allow Spring to find an instance of the respective context
  // whenever it needs to instantiate a Kalix component requiring a context
  private val actionCreationContextFactoryBean: ActionCreationContextFactoryBean =
    new ActionCreationContextFactoryBean(threadLocalActionContext)

  private val valueEntityContext: ValueEntityContextFactoryBean =
    new ValueEntityContextFactoryBean(threadLocalValueEntityContext)

  private val viewCreationContext: ViewCreationContextFactoryBean =
    new ViewCreationContextFactoryBean(threadLocalViewContext)

  kalixBeanFactory.registerSingleton("actionCreationContextFactoryBean", actionCreationContextFactoryBean)
  kalixBeanFactory.registerSingleton("valueEntityContext", valueEntityContext)
  kalixBeanFactory.registerSingleton("viewCreationContext", viewCreationContext)

  // TODO: it should not be allowed to annotate Kalix components with Spring stereotypes
  //  otherwise it will be possible to inject kalix components everywhere and that won't work as expected
  // therefore, we should check all beans in ApplicationContext and fail if any of them implements a Kalix component

  // This little hack allows us to find out which bean is annotated with SpringBootApplication (usually only one).
  // We need it to find out which packages to scan.
  // Normally, users are expected to have their classes in subpackages of their Main class.
  val springBootMain =
    applicationContext.getBeansWithAnnotation(classOf[SpringBootApplication]).values().asScala

  // TODO: users may define their Kalix components in other packages as well and then use @ComponentScan
  // to let Spring find them. We should also look for @ComponentScan in the Main class and collect any
  // scan package declared there
  val packagesToScan = springBootMain.map(_.getClass.getPackageName).toSet

  val provider = new KalixComponentProvider
  // all Kalix components found in the classpath
  packagesToScan
    .flatMap(pkg => provider.findKalixComponents(pkg))
    .foreach { kalixComponent =>

      val clz = Class.forName(kalixComponent.getBeanClassName)

      kalixBeanFactory.registerBeanDefinition(kalixComponent.getBeanClassName, kalixComponent)

      if (classOf[Action].isAssignableFrom(clz)) {
        logger.info(s"Registering Action provider for [${clz.getName}]")
        kalix.register(actionProvider(clz.asInstanceOf[Class[Action]]))
      }

      if (classOf[ValueEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering ValueEntity provider for [${clz.getName}]")
        kalix.register(valueEntityProvider(clz.asInstanceOf[Class[ValueEntity[Nothing]]]))
      }

      if (classOf[View[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering View provider for [${clz.getName}]")
        kalix.register(viewProvider(clz.asInstanceOf[Class[View[Nothing]]]))
      }

    // TODO: missing EventSourced and Replicated Entities
    }

  def start() = {
    logger.info("Starting Kalix Server!")
    kalix.createRunner(config).run()
  }

  /* Each component may have a creation context passed to its constructor.
   * This method checks if there is a constructor in `clz` that receives a `context`.
   */
  private def hasContextConstructor(clz: Class[_], contextType: Class[_]): Boolean =
    clz.getConstructors.exists { ctor =>
      ctor.getParameterTypes.contains(contextType)
    }

  private def actionProvider[A <: Action](clz: Class[A]): ActionProvider[A] =
    if (hasContextConstructor(clz, classOf[ActionCreationContext]))
      ReflectiveActionProvider.of(
        clz,
        context => {
          threadLocalActionContext.set(context)
          kalixBeanFactory.getBean(clz)
        })
    else
      ReflectiveActionProvider.of(clz, _ => kalixBeanFactory.getBean(clz))

  private def valueEntityProvider[S, E <: ValueEntity[S]](clz: Class[E]): ValueEntityProvider[S, E] = {
    if (hasContextConstructor(clz, classOf[ValueEntityContext]))
      ReflectiveValueEntityProvider.of(
        clz,
        context => {
          threadLocalValueEntityContext.set(context)
          kalixBeanFactory.getBean(clz)
        })
    else
      ReflectiveValueEntityProvider.of(clz, _ => kalixBeanFactory.getBean(clz))
  }

  private def viewProvider[S, V <: View[S]](clz: Class[V]): ViewProvider[S, V] = {
    if (hasContextConstructor(clz, classOf[ViewCreationContext]))
      ReflectiveViewProvider.of(
        clz,
        context => {
          threadLocalViewContext.set(context)
          kalixBeanFactory.getBean(clz)
        })
    else
      ReflectiveViewProvider.of(clz, _ => kalixBeanFactory.getBean(clz))
  }
}
