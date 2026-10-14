.DEFAULT_GOAL := help

# The plugin modules are the directories with their own Makefile; its plugin target builds the
# module's zip. Adding that Makefile is enough for a new plugin to join the build, the demo and
# the customer zip.
PLUGINS := $(patsubst %/Makefile,%,$(sort $(wildcard */Makefile)))
PLUGIN_TARGETS := $(addsuffix -plugin,$(PLUGINS))

# demo must stay .PHONY: the demo/ directory would otherwise satisfy the target as "up to date".
.PHONY: help dev-setup format lint test verify clean compile package install plugins $(PLUGIN_TARGETS) demo-plugins demo demo-up demo-down demo-logs demo-smoke demo-export demo-dist demo-clean

# The GeoServer plugin-family demo (see demo/README.md).
DEMO := demo
COMPOSE := docker compose -f $(DEMO)/docker-compose.yml
DIST := target/tileverse-geoserver-demo
URL := http://localhost:8080/geoserver
# Extra flags forwarded to the plugin Maven build, e.g. MVN_FLAGS=-Drevision=1.0.0 for a release
# (the POM uses CI-friendly ${revision} versioning; the release tag drives the version).
MVN_FLAGS ?=

# The Maven project list of the plugin modules, e.g. :tileverse-geoserver-parquetry,:tileverse-geoserver-cog
comma := ,
space := $(subst ,, )
PLUGIN_PROJECTS := $(subst $(space),$(comma),$(addprefix :tileverse-geoserver-,$(PLUGINS)))

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "; printf "Targets:\n"} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "  \033[36m%-20s\033[0m %s\n" "<module>-plugin" "Build one plugin zip: $(PLUGIN_TARGETS)"

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

# The GeoServer plugins and their demo. plugins builds every plugin zip in one reactor pass; the
# <module>-plugin targets build one zip each through that module's Makefile. The demo targets
# stage the zips into demo/build as plugin-<module>.zip, bake them into a Docker image that
# unzips them into GeoServer's WEB-INF/lib (see demo/Dockerfile), and drive it with docker compose.
plugins: ## Build every GeoServer plugin zip (drop into any GeoServer install)
	./mvnw -ntp $(MVN_FLAGS) -pl $(PLUGIN_PROJECTS) -am -Passembly package -Dmaven.test.skip=true

$(PLUGIN_TARGETS): %-plugin:
	$(MAKE) -C $* plugin

# Stage the plugin zips into demo/build, where the demo Dockerfile picks them up. The image
# installs each zip found there, and stale zips staged on another branch are removed first.
demo-plugins: plugins
	mkdir -p $(DEMO)/build
	rm -f $(DEMO)/build/plugin-*.zip
	for module in $(PLUGINS); do \
	  cp $$module/target/tileverse-geoserver-$$module-*-plugin.zip $(DEMO)/build/plugin-$$module.zip || exit 1; \
	done

demo: demo-plugins ## Build the demo Docker image
	$(COMPOSE) build

demo-up: demo ## Build and start the GeoServer demo
	$(COMPOSE) up -d
	@echo ""
	@echo "GeoServer is starting at $(URL)  (admin / geoserver)"
	@echo "Layer preview: $(URL)/web  ->  Layer Preview  ->  parquetry:world"
	@echo "Follow startup with: make demo-logs"

demo-down: ## Stop and remove the demo containers; the GeoServer configuration is kept
	$(COMPOSE) down

# Copies the catalog subtrees edited through the web admin or the REST API back into the baked-in
# data directory, to commit UI-made configuration as demo defaults.
demo-export: ## Capture the running demo's workspaces, styles and layer groups into demo/geoserver-data
	@for d in workspaces styles layergroups; do \
	  if $(COMPOSE) exec -T geoserver test -d /opt/geoserver/data_dir/$$d; then \
	    rm -rf $(DEMO)/geoserver-data/$$d && $(COMPOSE) cp geoserver:/opt/geoserver/data_dir/$$d $(DEMO)/geoserver-data/$$d; \
	  fi; \
	done
	@echo "exported into $(DEMO)/geoserver-data; review with git status"

demo-logs: ## Follow the demo container logs
	$(COMPOSE) logs -f

# The checks run by CI (see .github/workflows/geoserver-demo-smoke.yml): core.sh first, then the
# other scripts in demo/smoke, where each plugin adds its own. lib.sh holds their shared
# functions. Needs a running demo, and duckdb on PATH for the output-format checks.
SMOKE_CHECKS := $(DEMO)/smoke/core.sh $(filter-out %/core.sh %/lib.sh,$(sort $(wildcard $(DEMO)/smoke/*.sh)))
demo-smoke: ## Run the smoke checks against a running demo
	@for check in $(SMOKE_CHECKS); do \
	  echo "== $$check"; \
	  GEOSERVER_URL=$(URL) $$check || exit 1; \
	done

demo-dist: demo-plugins ## Build the self-contained customer demo zip
	rm -rf $(DIST) $(DIST).zip
	mkdir -p $(DIST)/build
	cp $(DEMO)/Dockerfile $(DEMO)/.dockerignore $(DEMO)/docker-compose.yml $(DIST)/
	cp $(DEMO)/dist-README.md $(DIST)/README.md
	cp $(DEMO)/build/plugin-*.zip $(DIST)/build/
	cp -r $(DEMO)/geoserver-data $(DIST)/geoserver-data
	cp -r $(DEMO)/data $(DIST)/data
	cp -r $(DEMO)/secrets $(DIST)/secrets
	cd target && zip -rq $(notdir $(DIST)).zip $(notdir $(DIST))
	@echo "distributable: $(CURDIR)/$(DIST).zip"

demo-clean: ## Remove the demo containers, the saved GeoServer configuration, build artifacts and the image
	$(COMPOSE) down -v --remove-orphans
	rm -rf $(DEMO)/build $(DIST) $(DIST).zip
	docker image rm tileverse-geoserver:demo 2>/dev/null || true
