# Parquetry plugin packaging. Run `make help` for the target list.

REPO_ROOT := ..
# Extra flags forwarded to the plugin Maven build, e.g. MVN_FLAGS=-Drevision=1.0.0 for a release
# (the POM uses CI-friendly ${revision} versioning; the release tag drives the version).
MVN_FLAGS ?=

.DEFAULT_GOAL := help
.PHONY: help plugin

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "; printf "Targets:\n"} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

plugin: ## Build the GeoServer plugin zip
	cd $(REPO_ROOT) && ./mvnw -ntp $(MVN_FLAGS) -pl :tileverse-geoserver-parquetry -am -Passembly package -Dmaven.test.skip=true
