.DEFAULT_GOAL := help
# demo must stay .PHONY: the demo/ directory would otherwise satisfy the target as "up to date".
.PHONY: help dev-setup format lint test verify clean compile package install geoserver-plugins demo-plugins demo demo-up demo-down demo-logs demo-dist demo-clean

# The GeoServer plugin-family demo (see demo/README.md).
DEMO := demo
COMPOSE := docker compose -f $(DEMO)/docker-compose.yml
DIST := target/tileverse-geoserver-demo
URL := http://localhost:8080/geoserver
# Extra flags forwarded to the plugin Maven build, e.g. MVN_FLAGS=-Drevision=1.0.0 for a release
# (the POM uses CI-friendly ${revision} versioning; the release tag drives the version).
MVN_FLAGS ?=

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "; printf "Targets:\n"} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

dev-setup: ## Install parent POMs into the local repo (mvnw -N install)
	./mvnw -N install

format: ## Apply code formatting (mvnw validate)
	./mvnw validate

lint: ## Run static analysis (mvnw -Pqa validate)
	./mvnw -Pqa validate

test: ## Run unit tests
	./mvnw test

verify: ## Full build: unit + integration tests
	./mvnw verify

clean: ## Remove build output (mvnw clean)
	./mvnw clean

compile: ## Compile all modules
	./mvnw compile

package: ## Package all modules (skip tests)
	./mvnw package -DskipTests

install: ## Build and install all modules to the local repo
	./mvnw install

# The GeoServer plugins and their demo. geoserver-plugins builds every plugin zip in one reactor
# pass. The demo targets stage those zips into demo/build, bake them into a Docker image that
# unzips them into GeoServer's WEB-INF/lib (see demo/Dockerfile), and drive it with docker compose.
geoserver-plugins: ## Build the GeoServer plugin zips (drop into any GeoServer install)
	./mvnw -ntp $(MVN_FLAGS) -pl :tileverse-geoserver-parquetry -am -Passembly package -Dmaven.test.skip=true

# Stage the plugin zips into demo/build, where the demo Dockerfile picks them up.
demo-plugins: geoserver-plugins
	mkdir -p $(DEMO)/build
	cp parquetry/target/tileverse-geoserver-parquetry-*-plugin.zip $(DEMO)/build/plugin.zip

demo: demo-plugins ## Build the demo Docker image
	$(COMPOSE) build

demo-up: demo ## Build and start the GeoServer demo
	$(COMPOSE) up -d
	@echo ""
	@echo "GeoServer is starting at $(URL)  (admin / geoserver)"
	@echo "Layer preview: $(URL)/web  ->  Layer Preview  ->  parquetry:world"
	@echo "Follow startup with: make demo-logs"

demo-down: ## Stop and remove the demo containers
	$(COMPOSE) down

demo-logs: ## Follow the demo container logs
	$(COMPOSE) logs -f

demo-dist: demo-plugins ## Build the self-contained customer demo zip
	rm -rf $(DIST) $(DIST).zip
	mkdir -p $(DIST)/build $(DIST)/data
	cp $(DEMO)/Dockerfile $(DEMO)/.dockerignore $(DEMO)/docker-compose.yml $(DIST)/
	cp $(DEMO)/dist-README.md $(DIST)/README.md
	cp $(DEMO)/build/plugin.zip $(DIST)/build/plugin.zip
	cp -r $(DEMO)/geoserver-data $(DIST)/geoserver-data
	cp -r $(DEMO)/data/ne $(DIST)/data/ne
	cp -r $(DEMO)/data/iceberg-warehouse $(DIST)/data/iceberg-warehouse
	cp -r $(DEMO)/data/stac $(DIST)/data/stac
	cp $(DEMO)/data/catalog.json $(DIST)/data/
	cp -r $(DEMO)/secrets $(DIST)/secrets
	cd target && zip -rq $(notdir $(DIST)).zip $(notdir $(DIST))
	@echo "distributable: $(CURDIR)/$(DIST).zip"

demo-clean: demo-down ## Remove demo build artifacts and the demo image
	rm -rf $(DEMO)/build $(DIST) $(DIST).zip
	docker image rm tileverse-geoserver:demo 2>/dev/null || true
