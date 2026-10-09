import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc, deleteDoc, getDoc, writeBatch, query, collection, orderBy, startAt, endAt, getDocs } from 'firebase/firestore';
let env;
const zeros = { STEADY: 0, WOBBLY: 0, CHAOS: 0 };
const profile = (name='player_one') => ({username:name,key:name.toLowerCase(),best:{...zeros},daily:{...zeros},day:'2026-10-09'});
const db = uid => env.authenticatedContext(uid).firestore();
async function claim(uid,name) {
  const store=db(uid);const batch=writeBatch(store);
  batch.set(doc(store,'players',uid),profile(name));
  batch.set(doc(store,'handles',name.toLowerCase()),{owner:uid,username:name,key:name.toLowerCase()});
  await batch.commit();
}
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-off-balance',firestore:{rules:readFileSync(new URL('../firestore.rules',import.meta.url),'utf8'),host:'127.0.0.1',port:8080}})});
beforeEach(async()=>env.clearFirestore());
after(async()=>env.cleanup());
test('claim creates a player and reserves a case-insensitive handle atomically',async()=>{await assertSucceeds(claim('one','Player_One'));await assertFails(claim('two','player_one'))});
test('unauthenticated clients cannot read or write player data',async()=>{const store=env.unauthenticatedContext().firestore();await assertFails(getDoc(doc(store,'players','one')));await assertFails(setDoc(doc(store,'players','one'),profile()))});
test('another player cannot replace a score or username',async()=>{await claim('one','player_one');await assertFails(updateDoc(doc(db('two'),'players','one'),{best:{...zeros,STEADY:12}}))});
test('username prefix search finds real players',async()=>{await claim('one','rival_one');await claim('two','rival_two');await assertSucceeds(getDocs(query(collection(db('three'),'players'),orderBy('key'),startAt('rival_'),endAt('rival_\uf8ff'))))});
test('a player cannot publish without reserving the name',async()=>assertFails(setDoc(doc(db('one'),'players','one'),profile())));
test('rival can publish a new best in a difficulty',async()=>{await claim('one','rival_one');await assertSucceeds(updateDoc(doc(db('one'),'players','one'),{best:{...zeros,STEADY:12},daily:{...zeros,STEADY:12}}))});
test('all-time records cannot decrease',async()=>{await claim('one','player_one');const p=doc(db('one'),'players','one');await updateDoc(p,{best:{...zeros,STEADY:12},daily:{...zeros,STEADY:12}});await assertFails(updateDoc(p,{best:zeros,daily:zeros}))});
test('daily scores cannot decrease on the same day',async()=>{await claim('one','player_one');const p=doc(db('one'),'players','one');await updateDoc(p,{best:{...zeros,STEADY:12},daily:{...zeros,STEADY:12}});await assertFails(updateDoc(p,{daily:zeros}))});
test('new day resets daily scores without erasing personal best',async()=>{await claim('one','player_one');const p=doc(db('one'),'players','one');await updateDoc(p,{best:{...zeros,STEADY:12},daily:{...zeros,STEADY:12}});await assertSucceeds(updateDoc(p,{day:'2026-10-10',daily:zeros}))});
test('day cannot move backwards to overwrite a daily record',async()=>{await claim('one','player_one');await assertFails(updateDoc(doc(db('one'),'players','one'),{day:'2026-10-08'}))});
test('extra personal information is rejected',async()=>{await claim('one','player_one');await assertFails(updateDoc(doc(db('one'),'players','one'),{email:'fixture@example.test'}))});
test('invalid and fractional scores are rejected',async()=>{await claim('one','player_one');for(const value of [-1,1.5,100001])await assertFails(updateDoc(doc(db('one'),'players','one'),{best:{...zeros,STEADY:value}}))});
test('rename frees the old handle in the same transaction',async()=>{await claim('one','player_one');const store=db('one');const b=writeBatch(store);b.update(doc(store,'players','one'),{username:'player_new',key:'player_new'});b.set(doc(store,'handles','player_new'),{owner:'one',username:'player_new',key:'player_new'});b.delete(doc(store,'handles','player_one'));await assertSucceeds(b.commit());await assertSucceeds(claim('two','player_one'))});
test('a handle cannot be deleted while it is still in use',async()=>{await claim('one','player_one');await assertFails(deleteDoc(doc(db('one'),'handles','player_one')))});
test('another player cannot delete a rival profile or steal a handle',async()=>{await claim('one','player_one');await assertFails(deleteDoc(doc(db('two'),'players','one')));await assertFails(setDoc(doc(db('two'),'handles','player_one'),{owner:'two',username:'player_one',key:'player_one'}))});
test('legacy unauthenticated tap writes are rejected',async()=>assertFails(setDoc(doc(env.unauthenticatedContext().firestore(),'daily_leaderboards','2026-10-09','scores','one'),{today_count:100})));
test('purchase receipts and entitlements cannot be read or forged by clients',async()=>{
  await env.withSecurityRulesDisabled(async context=>setDoc(doc(context.firestore(),'style_entitlements','one'),{token:'fixture_purchase_token',productId:'off_balance_style_pack'}));
  await assertFails(getDoc(doc(db('one'),'style_entitlements','one')));
  await assertFails(getDoc(doc(db('two'),'style_entitlements','one')));
  await assertFails(setDoc(doc(db('one'),'style_entitlements','one'),{owned:true}));
});
