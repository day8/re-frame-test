# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## 0.1.6
### Added
- New test runner, `day8.chrome-shadow-test-runner`. Usage:
  1. In your project, compile a shadow-cljs [browser-test](https://shadow-cljs.github.io/docs/UsersGuide.html#target-browser-test).
  2. Invoke the tool. For instance, using an [alias](https://clojure.org/reference/deps_edn#aliases):
  ```clojure
  {...
   :aliases
   {:test
	{:extra-paths ["test"]
	 :extra-deps  {day8.re-frame/test {:local/root      "../re-frame-test"
									   #_#_:mvn/version "0.1.5"}}
	 :exec-fn     day8.chrome-shadow-test-runner/run
	 :exec-args   {:test-dir    "CHANGE_ME!_run/resources/public/compiled_test"
				   :chrome-path "CHANGE_ME!_path/to/chrome"}}}}
  ```
  Note the `:test-dir` and `:chrome-path` options.
  For Day8's use-case, this is enough to supercede karma-test (I think).
## 0.1.5
### Changed
- Add a new arity to `make-widget-async` to provide a different widget shape.
- Migrate to [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) and
  [lein-shadow](https://gitlab.com/nikperic/lein-shadow)
- Replace `*test-context*` dynvar of atoms with a `test-context*` atom of pure
  data.

## 0.1.1 - 2016-07-25
### Changed
- Documentation on how to make the widgets.

### Removed
- `make-widget-sync` - we're all async, all the time.

### Fixed
- Fixed widget maker to keep working when daylight savings switches over.

## 0.1.0 - 2016-07-25
### Added
- Files from the new template.
- Widget maker public API - `make-widget-sync`.

[Unreleased]: https://github.com/your-name/re-frame-test/compare/0.1.1...HEAD
[0.1.1]: https://github.com/your-name/re-frame-test/compare/0.1.0...0.1.1
