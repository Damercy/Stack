const {test}=require('node:test');
const assert=require('node:assert/strict');
const {validReceipt}=require('../lib/stylePack.js');
const paid={purchaseState:0,consumptionState:0,productId:'off_balance_style_pack'};
test('a completed nonconsumable receipt can grant the pack',()=>assert.equal(validReceipt(paid),true));
test('pending, cancelled and missing receipt states cannot grant anything',()=>{
  for(const purchaseState of [1,2,undefined])assert.equal(validReceipt({...paid,purchaseState}),false);
});
test('a different product receipt cannot unlock the pack',()=>assert.equal(validReceipt({...paid,productId:'unrelated_item'}),false));
test('a consumed or malformed receipt is rejected',()=>{
  assert.equal(validReceipt({...paid,consumptionState:1}),false);
  assert.equal(validReceipt({}),false);
});
