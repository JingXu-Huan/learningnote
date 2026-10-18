# Kafka 架构实验室

Primary target: `website/visualizations/kafka-architecture/index.html`

Mode: Read, with small Operate exercises.

Extend the existing learning visualization family. Inherit the established blue/white palette, Chinese typography, borders, spacing and focus treatment from DESIGN.md and the ticket architecture page. No global design token or product identity changes.

The reader follows one message through append, replication, acknowledgement and consumption, then manipulates offsets, partition assignments and payload capacity. Separate published company facts, engineering analysis and evidence limits in the evolution comparison.

All state and measurements are teaching examples. The page is self-contained and performs no Kafka, cloud or business API calls. The source version is Apache Kafka 4.3, with companion source reading pinned to 4.3.1. Mobile diagrams use a vertical arrangement rather than shrinking unreadable desktop labels. Controls retain keyboard operation, selected states, native labels, reduced motion and explicit validation.

Publication must use the existing explicit VISUAL_PAGES allowlist. Markdown companions are the canonical searchable long-form chapters; the standalone graph page links back to their stable generated routes.
