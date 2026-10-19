# Go × Java 内存实验室

Primary target: website/visualizations/go-java-memory/index.html

Mode: Read, with step-through Operate interactions.

Extend the existing learning visualization family. Reuse DESIGN.md and the existing blue/white Chinese reading surfaces. Do not change global tokens or product context. The user's request fixes the subject and destination: visualize Go heap management, compare Java, and publish to the existing notes website.

## Direction contract

THESIS: Follow object references through allocation, retention and collection; distinguish runtime storage from concurrent memory semantics.

OWN-WORLD: White reading surfaces, blue interaction emphasis, green reuse states, text and line styles accompanying all semantic colors. Chinese system typography; monospace only for code and measurements.

STORY: Inspect the two memory maps, replay object lifetimes, compare mcache and TLAB, follow Go and G1 GC, then establish happens-before with channel and volatile. A searchable Markdown companion supplies deeper explanations and official source links.

FIRST VIEWPORT: Compact title and local navigation; paired Go and Java logical maps occupy the main reading area. Every region is directly selectable, with the selected explanation immediately beneath the maps. Mobile stacks the maps while preserving readable text.

FORM: Code-led extension of the established visualization family; direct comparison and state replays. The concrete subject and existing site style resolve the structure without a visual-world selection round. Signature interaction: an object's identity remains stable while references and storage state change.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Constraints

- Go source baseline 1.26.0; Java SE 25 and HotSpot/G1 examples. They are declared baselines, not claims of latest releases.
- Logical diagrams, object sizes, GC sequence and timings are illustrative. No runtime measurement or benchmark claims.
- JVMS regions, HotSpot implementation and JMM are distinct.
- Retain keyboard tabs, visible focus, native select controls, live step explanations, reduced motion and mobile reading.
- Publish this one self-contained page through VISUAL_PAGES. No global attachment publication change.
- No raster assets ship. Screenshots are local review evidence only.
