.PHONY: lint
lint:
	cljstyle check
	clj-kondo --lint src:test

.PHONY: install
install:
	clojure -T:build install

.PHONY: bump-minor-version
bump-minor-version:
	clojure -T:build bump-minor-version

.PHONY: test
test:
	clojure -M:dev:test

.PHONY: outdated
outdated:
	clojure -M:outdated --upgrade

.PHONY: clean
clean:
	rm -rf .cpcache target
