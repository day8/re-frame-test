# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## 0.1.6
### Added
- Test runner: `day8.chrome-shadow-test-runner`

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
