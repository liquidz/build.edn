.PHONY: lint
lint:
	cljstyle check
	clj-kondo --lint src:test

.PHONY: install
install:
	clojure -T:build install

.PHONY: test
test:
	clojure -M:dev:test

.PHONY: clean
clean:
	rm -rf .cpcache target
