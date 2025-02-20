# Make Kalix Java SDK documentation

upstream := lightbend/kalix-jvm-sdk
branch   := docs/kalix-current
sources  := src build/src/managed

src_managed := build/src/managed
java_pb_managed_attachments := ${src_managed}/modules/java-protobuf/attachments
java_pb_managed_examples := ${src_managed}/modules/java-protobuf/examples
java_pb_managed_partials := ${src_managed}/modules/java-protobuf/partials

scala_pb_managed_attachments := ${src_managed}/modules/scala-protobuf/attachments

java_managed_attachments := ${src_managed}/modules/java/attachments
java_managed_examples := ${src_managed}/modules/java/examples
java_managed_partials := ${src_managed}/modules/java/partials


antora_docker_image := gcr.io/kalix-public/kalix-docbuilder
antora_docker_image_tag := 0.0.5
root_dir := $(shell git rev-parse --show-toplevel)
base_path := $(shell git rev-parse --show-prefix)

# Centralized entrypoint detection
ifeq ($(shell uname -s | grep -E 'MINGW|CYGWIN|MSYS'),)
  ENTRYPOINT = /bin/sh
else
  ENTRYPOINT = //bin/sh
endif

.SILENT:

build: dev

clean:
	rm -rf build

prepare:
	mkdir -p "${src_managed}"
	cp src/antora.yml "${src_managed}/antora.yml"

managed: prepare attributes apidocs examples bundles

attributes:
	mkdir -p "${java_pb_managed_partials}"
	bin/version.sh | xargs -0  printf ":kalix-jvm-sdk-version: %s" \
		> "${java_pb_managed_partials}/attributes.adoc"
	echo ":java-pb-version: 11" \
		>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":java-version: 17" \
		>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":minimum_maven_version: 3.6" \
		>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":minimum_sbt_version: 1.3.6" \
    	>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":minimum_docker_version: 20.10.14" \
		>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":java_minimum_sdk_version: 0.7.0" \
		>> "${java_pb_managed_partials}/attributes.adoc"
	echo ":console: https://console.kalix.io/" \
		>> "${java_pb_managed_partials}/attributes.adoc"

	mkdir -p "${java_managed_partials}"
	cp "${java_pb_managed_partials}/attributes.adoc" "${java_managed_partials}/attributes.adoc"

apidocs:
	mkdir -p "${java_pb_managed_attachments}"
	mkdir -p "${java_managed_attachments}"
	cd .. && sbt javaSdkProtobuf/doc javaSdkProtobufTestKit/doc scalaSdkProtobuf/doc scalaSdkProtobufTestKit/doc
	rsync -a ../sdk/java-sdk-protobuf/target/api/ "${java_pb_managed_attachments}/api/"
	rsync -a ../sdk/java-sdk-protobuf-testkit/target/api/ "${java_pb_managed_attachments}/testkit/"
	rsync -a ../sdk/scala-sdk-protobuf/target/scala-2.13/api/ "${java_pb_managed_attachments}/scala-api/"
	rsync -a ../sdk/scala-sdk-protobuf-testkit/target/scala-2.13/api/ "${java_pb_managed_attachments}/scala-testkit-api/"
	rsync -a ../sdk/java-sdk-protobuf/target/api/ "${java_managed_attachments}/api/"
	rsync -a ../sdk/java-sdk-protobuf-testkit/target/api/ "${java_managed_attachments}/testkit/"
	mkdir -p "${scala_pb_managed_attachments}"
	bin/version.sh > "${java_pb_managed_attachments}/latest-version.txt" \
		&& cp "${java_pb_managed_attachments}/latest-version.txt" "${scala_pb_managed_attachments}" \
		&& cp "${java_pb_managed_attachments}/latest-version.txt" "${java_managed_attachments}"

examples:
	mkdir -p "${java_pb_managed_examples}"
	rsync -a --exclude-from=.examplesignore ../samples/java-protobuf-* "${java_pb_managed_examples}/"
	rsync -a --exclude-from=.examplesignore ../samples/scala-protobuf-* "${java_pb_managed_examples}/"

bundles:
	bin/bundle.sh --zip "${java_pb_managed_attachments}/java-protobuf-customer-registry-quickstart.zip" ../samples/java-protobuf-customer-registry-quickstart
	bin/bundle.sh --zip "${java_pb_managed_attachments}/java-protobuf-customer-registry-views-quickstart.zip" ../samples/java-protobuf-customer-registry-views-quickstart
	bin/bundle.sh --zip "${java_pb_managed_attachments}/java-protobuf-customer-registry-kafka-quickstart.zip" ../samples/java-protobuf-customer-registry-kafka-quickstart
	bin/bundle.sh --zip "${java_pb_managed_attachments}/java-protobuf-shopping-cart-quickstart.zip" ../samples/java-protobuf-shopping-cart-quickstart
	bin/bundle.sh --zip "${java_pb_managed_attachments}/scala-protobuf-customer-registry-quickstart.zip" ../samples/scala-protobuf-customer-registry-quickstart

dev: clean managed validate-xrefs dev-html

# like dev but without apidocs, bundles and testkits. Useful for fast dev cycles
quick-dev: clean prepare attributes examples dev-html

dev-html:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint $(ENTRYPOINT) \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${base_path} && antora --cache-dir=.cache/antora --stacktrace --log-failure-level=warn dev/antora.yml"
	@echo "Generated docs at dev/build/site/java/index.html"

validate-xrefs:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint $(ENTRYPOINT) \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c 'cd /antora/${base_path} && NODE_PATH="$$(npm -g root)" antora --generator @antora/xref-validator dev/antora.yml'

validate-links:
	docker run \
		-v ${root_dir}:/antora \
		--rm \
		--entrypoint $(ENTRYPOINT) \
		-t ${antora_docker_image}:${antora_docker_image_tag} \
		-c "cd /antora/${base_path} && find src -name '*.adoc' -print0 | xargs -0 -n1 asciidoc-link-check --progress --config config/validate-links.json"

deploy: clean managed
	bin/deploy.sh --module java --upstream ${upstream} --branch ${branch} ${sources}
