const assert = require("node:assert/strict");
const { test } = require("node:test");
const { prepareDocuments, rankDocuments } = require("./assets/search-worker.js");

const documents = prepareDocuments([
  { title: "操作系统", text: "进程与内存管理", location: "notes/os/" },
  { title: "网络层", text: "操作系统通过 ARP 查找下一跳地址", location: "notes/network/" },
  { title: "内存", text: "虚拟内存：地址空间", location: "notes/memory/" },
]);

test("Chinese phrases work without spaces and exact titles rank first", () => {
  assert.deepEqual(rankDocuments(documents, "操作系统").map((item) => item.location), ["notes/os/", "notes/network/"]);
});
test("Two-character Chinese queries and normalized English work", () => {
  assert.equal(rankDocuments(documents, "内存").length, 2);
  assert.equal(rankDocuments(documents, "ａｒｐ")[0].location, "notes/network/");
});
test("All terms must match; blank and unknown queries have no results", () => {
  assert.equal(rankDocuments(documents, "操作系统 下一跳").length, 1);
  assert.equal(rankDocuments(documents, " ").length, 0);
  assert.equal(rankDocuments(documents, "不存在").length, 0);
});
