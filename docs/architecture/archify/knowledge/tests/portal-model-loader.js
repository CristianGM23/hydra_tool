/* Loads the HYDRA portal content model (HM) in node: model -> ext -> lens -> tech, exactly as hydra-architecture.html does.
   Shared by the test-evidence tools (generator, validator, builder). Returns { HM, dir }. */
"use strict";
const fs = require("fs"), path = require("path");
const dir = path.resolve(__dirname, "..", "..");
let HM = null;
module.exports = function load() {
  if (HM) return { HM, dir };
  global.window = global;
  (0, eval)(fs.readFileSync(path.join(dir, "hydra-portal-model.js"), "utf8"));
  const ld = (re) => fs.readdirSync(dir).filter((f) => re.test(f)).sort().forEach((f) => (0, eval)(fs.readFileSync(path.join(dir, f), "utf8")));
  ld(/^hydra-model-.*\.js$/); ld(/^hydra-ext-.*\.js$/); ld(/^hydra-lens-.*\.js$/); ld(/^hydra-tech-.*\.js$/);
  HM = global.HM; return { HM, dir };
};
