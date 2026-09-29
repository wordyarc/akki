.PHONY: build test ci clean coverage bench update-abi update-test-data
.NOTPARALLEL:

build:
	./gradlew assemble

test:
	./gradlew test allTests

ci:
	./gradlew build --continue
	./gradlew lincheck

clean:
	./gradlew clean

coverage:
	./gradlew :koverHtmlReport

bench:
	./gradlew :akki-benchmark:benchmark --no-parallel --max-workers=1

update-abi:
	./gradlew updateKotlinAbi

update-test-data:
	./gradlew :akki-compiler:test -Pkotlin.test.update.test.data=true
