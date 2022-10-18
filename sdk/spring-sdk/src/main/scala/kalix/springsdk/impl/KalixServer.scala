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
import scala.jdk.OptionConverters.RichOption
import scala.util.Try
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kalix.javasdk.Kalix
import kalix.javasdk.action.Action
import kalix.javasdk.action.ActionCreationContext
import kalix.javasdk.action.ActionProvider
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext
import kalix.javasdk.eventsourcedentity.EventSourcedEntityProvider
import kalix.javasdk.impl.GrpcClients
import kalix.javasdk.replicatedentity.ReplicatedEntity
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.valueentity.ValueEntityContext
import kalix.javasdk.valueentity.ValueEntityProvider
import kalix.javasdk.view.View
import kalix.javasdk.view.ViewCreationContext
import kalix.javasdk.view.ViewProvider
import kalix.springsdk.KalixClient
import kalix.springsdk.SpringSdkBuildInfo
import kalix.springsdk.action.ReflectiveActionProvider
import kalix.springsdk.eventsourced.ReflectiveEventSourcedEntityProvider
import kalix.springsdk.impl.KalixServer.ActionCreationContextFactoryBean
import kalix.springsdk.impl.KalixServer.EventSourcedEntityContextFactoryBean
import kalix.springsdk.impl.KalixServer.KalixClientFactoryBean
import kalix.springsdk.impl.KalixServer.KalixComponentProvider
import kalix.springsdk.impl.KalixServer.MainClassProvider
import kalix.springsdk.impl.KalixServer.ValueEntityContextFactoryBean
import kalix.springsdk.impl.KalixServer.ViewCreationContextFactoryBean
import kalix.springsdk.valueentity.ReflectiveValueEntityProvider
import kalix.springsdk.view.ReflectiveViewProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanCreationException
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

  val kalixComponents: Seq[Class[_]] =
    classOf[Action] ::
    classOf[EventSourcedEntity[_]] ::
    classOf[ValueEntity[_]] ::
    classOf[ReplicatedEntity[_]] ::
    classOf[View[_]] ::
    Nil

  private val kalixComponentsNames = kalixComponents.map(_.getName)

  /**
   * Classpath scanning provider that will lookup for the original main class. Spring doesn't make the original main
   * class available in the application context, but a cglib enhanced variant.
   *
   * The enhanced variant doesn't contain all the annotations, but only the SpringBootApplication one. Therefore we need
   * to lookup for the original one. We need it to find the default ACL annotation.
   */
  class MainClassProvider(cglibMain: Class[_]) extends ClassPathScanningCandidateComponentProvider {

    private object OriginalMainClassFilter extends TypeFilter {
      override def `match`(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory): Boolean = {
        // in the classpath, we should have another class annotated with SpringBootApplication
        // this is the original class that generated the cglib enhanced one
        metadataReader.getAnnotationMetadata.hasAnnotation(classOf[SpringBootApplication].getName)
      }
    }

    addIncludeFilter(OriginalMainClassFilter)

    def findOriginalMailClass: Class[_] =
      this
        .findCandidateComponents(cglibMain.getPackageName)
        .asScala
        .map { bean => Class.forName(bean.getBeanClassName) }
        .head

  }

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
  class KalixComponentProvider(cglibMain: Class[_]) extends ClassPathScanningCandidateComponentProvider {

    private object KalixComponentTypeFilter extends TypeFilter {
      override def `match`(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory): Boolean = {
        kalixComponentsNames.contains(metadataReader.getClassMetadata.getSuperClassName)

      }
    }

    addIncludeFilter(KalixComponentTypeFilter)

    // TODO: users may define their Kalix components in other packages as well and then use @ComponentScan
    // to let Spring find them. We should also look for @ComponentScan in the Main class and collect any
    // scan package declared there. So later, packageToScan will be a List of packages
    def findKalixComponents: Seq[BeanDefinition] = {
      findCandidateComponents(cglibMain.getPackageName).asScala.map { bean =>
        // by default, the provider set them all as singletons,
        // we need to make them all a prototype
        bean.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)

        // making it only wireable by constructor will simplify our lives
        // we can review it later, if needed
        bean.asInstanceOf[AbstractBeanDefinition].setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR)
        bean

      }.toSeq
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

  class EventSourcedEntityContextFactoryBean(loco: ThreadLocal[EventSourcedEntityContext])
      extends FactoryBean[EventSourcedEntityContext] {
    override def isSingleton: Boolean = false // never!!
    override def getObject: EventSourcedEntityContext = loco.get()
    override def getObjectType: Class[_] = classOf[EventSourcedEntityContext]
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

  class KalixClientFactoryBean(loco: ThreadLocal[KalixClient]) extends FactoryBean[KalixClient] {
    override def isSingleton: Boolean = true // yes, we only need one
    override def getObject: KalixClient = {
      if (loco.get() != null) loco.get()
      else
        throw new BeanCreationException("KalixClient can only be injected in Kalix Actions.")
    }

    override def getObjectType: Class[_] = classOf[KalixClient]
  }
}

case class KalixServer(applicationContext: ApplicationContext, config: Config) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val messageCodec = new SpringSdkMessageCodec
  private val kalixClient = new RestKalixClientImpl(messageCodec)
  private val threadLocalActionContext = new ThreadLocal[ActionCreationContext]
  private val threadLocalEventSourcedEntityContext = new ThreadLocal[EventSourcedEntityContext]
  private val threadLocalValueEntityContext = new ThreadLocal[ValueEntityContext]
  private val threadLocalViewContext = new ThreadLocal[ViewCreationContext]
  private val threadLocalKalixClient = new ThreadLocal[KalixClient]

  private val kalixBeanFactory = new DefaultListableBeanFactory(applicationContext)

  // the FactoryBeans below will allow Spring to find an instance of the respective context
  // whenever it needs to instantiate a Kalix component requiring a context
  private val actionCreationContextFactoryBean: ActionCreationContextFactoryBean =
    new ActionCreationContextFactoryBean(threadLocalActionContext)

  private val eventSourcedEntityContextFactoryBean: EventSourcedEntityContextFactoryBean =
    new EventSourcedEntityContextFactoryBean(threadLocalEventSourcedEntityContext)

  private val valueEntityContextFactoryBean: ValueEntityContextFactoryBean =
    new ValueEntityContextFactoryBean(threadLocalValueEntityContext)

  private val viewCreationContextFactoryBean: ViewCreationContextFactoryBean =
    new ViewCreationContextFactoryBean(threadLocalViewContext)

  private val kalixClientFactoryBean: KalixClientFactoryBean =
    new KalixClientFactoryBean(threadLocalKalixClient)

  kalixBeanFactory.registerSingleton("actionCreationContextFactoryBean", actionCreationContextFactoryBean)
  kalixBeanFactory.registerSingleton("eventSourcedEntityContext", eventSourcedEntityContextFactoryBean)
  kalixBeanFactory.registerSingleton("valueEntityContext", valueEntityContextFactoryBean)
  kalixBeanFactory.registerSingleton("viewCreationContext", viewCreationContextFactoryBean)
  kalixBeanFactory.registerSingleton("kalixClient", kalixClientFactoryBean)

  // there should be only one class annotated with SpringBootApplication in the applicationContext
  private val cglibEnhanceMainClass =
    applicationContext.getBeansWithAnnotation(classOf[SpringBootApplication]).values().asScala.head

  // lookup for the original main class, not the one enhanced by CGLIB
  private val mainClass = new MainClassProvider(cglibEnhanceMainClass.getClass).findOriginalMailClass

  val kalix: Kalix = (new Kalix)
    .withSdkName(SpringSdkBuildInfo.name)
    .withDefaultAclFileDescriptor(AclDescriptorFactory.defaultAclFileDescriptor(mainClass).toJava)

  private val provider = new KalixComponentProvider(cglibEnhanceMainClass.getClass)

  // all Kalix components found in the classpath
  provider.findKalixComponents
    .foreach { bean =>

      val clz = Class.forName(bean.getBeanClassName)

      kalixBeanFactory.registerBeanDefinition(bean.getBeanClassName, bean)

      if (classOf[Action].isAssignableFrom(clz)) {
        logger.info(s"Registering Action provider for [${clz.getName}]")
        val action = actionProvider(clz.asInstanceOf[Class[Action]])
        kalix.register(action)
        kalixClient.registerComponent(action.serviceDescriptor())
      }

      if (classOf[EventSourcedEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering EventSourcedEntity provider for [${clz.getName}]")
        val esEntity = eventSourcedEntityProvider(clz.asInstanceOf[Class[EventSourcedEntity[Nothing]]])
        kalix.register(esEntity)
        kalixClient.registerComponent(esEntity.serviceDescriptor())
      }

      if (classOf[ValueEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering ValueEntity provider for [${clz.getName}]")
        val valueEntity = valueEntityProvider(clz.asInstanceOf[Class[ValueEntity[Nothing]]])
        kalix.register(valueEntity)
        kalixClient.registerComponent(valueEntity.serviceDescriptor())
      }

      if (classOf[View[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering View provider for [${clz.getName}]")
        val view = viewProvider(clz.asInstanceOf[Class[View[Nothing]]])
        kalix.register(view)
        kalixClient.registerComponent(view.serviceDescriptor())
      }

    // TODO: missing Replicated Entities
    }

  def start() = {
    logger.info("Starting Kalix Server!")

    val finalConfig =
      ConfigFactory
        // it doesn't make sense to try to load descriptor source for
        // the Spring SDK, so better to just disable it
        .parseString("kalix.discovery.protobuf-descriptor-with-source-info-path=disabled")
        .withFallback(config)

    kalix.createRunner(finalConfig).run()
  }

  /* Each component may have a creation context passed to its constructor.
   * This method checks if there is a constructor in `clz` that receives a `context`.
   */
  private def hasContextConstructor(clz: Class[_], contextType: Class[_]): Boolean =
    clz.getConstructors.exists { ctor =>
      ctor.getParameterTypes.contains(contextType)
    }

  private def actionProvider[A <: Action](clz: Class[A]): ActionProvider[A] =
    ReflectiveActionProvider.of(
      clz,
      messageCodec,
      context => {
        if (hasContextConstructor(clz, classOf[ActionCreationContext]))
          threadLocalActionContext.set(context)

        if (hasContextConstructor(clz, classOf[KalixClient])) {
          val grpcClients = GrpcClients(context.materializer().system)
          grpcClients.getProxyHostname.foreach(kalixClient.setHost)
          grpcClients.getProxyPort.foreach(kalixClient.setPort)
          grpcClients.getIdentificationInfo.foreach(kalixClient.setIdentificationInfo)
          threadLocalKalixClient.set(kalixClient)
        }

        kalixBeanFactory.getBean(clz)
      })

  private def eventSourcedEntityProvider[S, E <: EventSourcedEntity[S]](
      clz: Class[E]): EventSourcedEntityProvider[S, E] =
    ReflectiveEventSourcedEntityProvider.of(
      clz,
      messageCodec,
      context => {
        if (hasContextConstructor(clz, classOf[EventSourcedEntityContext]))
          threadLocalEventSourcedEntityContext.set(context)
        kalixBeanFactory.getBean(clz)
      })

  private def valueEntityProvider[S, E <: ValueEntity[S]](clz: Class[E]): ValueEntityProvider[S, E] =
    ReflectiveValueEntityProvider.of(
      clz,
      messageCodec,
      context => {
        if (hasContextConstructor(clz, classOf[ValueEntityContext]))
          threadLocalValueEntityContext.set(context)
        kalixBeanFactory.getBean(clz)
      })

  private def viewProvider[S, V <: View[S]](clz: Class[V]): ViewProvider[S, V] =
    ReflectiveViewProvider.of(
      clz,
      messageCodec,
      context => {
        if (hasContextConstructor(clz, classOf[ViewCreationContext]))
          threadLocalViewContext.set(context)
        kalixBeanFactory.getBean(clz)
      })
}
