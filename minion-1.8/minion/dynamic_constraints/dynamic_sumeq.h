/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#ifndef CONSTRAINT_DYNAMIC_SUMEQ_H
#define CONSTRAINT_DYNAMIC_SUMEQ_H

//ONLY WORKS FOR VARS WHERE ARBITRARY VALUES CAN BE REMOVED AT ANY TIME

template<typename VarRef1, typename VarRef2, typename VarRef3>
struct SumEqConstraintDynamic : public AbstractConstraint
{
  virtual string constraint_name()
  { return "SumEqDynamic"; }
  
  VarRef1 x;
  VarRef2 y;
  VarRef3 z;
  
  SysInt xmult, ymult;

  SumEqConstraintDynamic(StateObj* _stateObj, SysInt _xmult, SysInt _ymult, VarRef1 _x, VarRef2 _y, VarRef3 _z) :
    AbstractConstraint(stateObj), xmult(_xmult), ymult(_ymult), x(_x), y(_y), z(_z), vals(-1)
  { 
#ifndef WATCHEDLITERALS
    cerr << "This almost certainly isn't going to work... sorry" << endl;
#endif
    if(xmult < 1 || ymult < 1)
      INPUT_ERROR("Multipliers on gacsum must be > 0");

  }

  SysInt vals;
  
  virtual SysInt dynamic_trigger_count() //need two watched literals per value in each var (one per support) 
  {
    if(vals == -1) { //not already calculated 
      vals = 0;
      DomainInt max = x.getMax(); 
      for(DomainInt i = x.getMin(); i <= max; i++) 
        if(x.inDomain(i)) vals++; 
      max = y.getMax(); 
      for(DomainInt i = y.getMin(); i <= max; i++) 
        if(y.inDomain(i)) vals++; 
      max = z.getMax(); 
      for(DomainInt i = z.getMin(); i <= max; i++) 
        if(z.inDomain(i)) vals++; 
    } 
    //printf("Count: %d\n", vals);
    return 2 * vals; 
    //return 1000;
  }
  
  vector<AnyVarRef> get_vars()
  {
    vector<AnyVarRef> a;
    a.reserve(3); a.push_back(x); a.push_back(y); a.push_back(z);
    return a;
  }

  BOOL check_assignment(DomainInt* v, SysInt v_size) {
    return xmult*v[0] + ymult*v[1] == v[2];
  }

  //use smart algorithm to find out if any a + b = c in linear time
  //puts supports for c in resArr and returns T, or F if none exist
  BOOL get_sumsupport(VarRef1& a, VarRef2& b, DomainInt c, SysInt (&resArr)[2]) {
    DomainInt bMin = b.getMin();
    DomainInt bMax = b.getMax();
    DomainInt aCurr = max(a.getMin(), (c - bMax*ymult)/xmult);
    DomainInt aMax = min(a.getMax(), (c - bMin*ymult)/xmult);

    while(aCurr <= aMax && !a.inDomain(aCurr))
      aCurr++;
    while(aCurr <= aMax) {
      DomainInt required = c - aCurr*xmult;
      DomainInt reqB = required / ymult;
      DomainInt modB = required % ymult;
      if(modB == 0 && b.inDomain(reqB)) {
        resArr[0] = aCurr;
        resArr[1] = reqB;
        D_ASSERT(resArr[0]*xmult + resArr[1]*ymult == c);
        return 1;
      } else {
        aCurr++;
        while(aCurr <= aMax && !(a.inDomain(aCurr)))
          aCurr++;
      }
    }
    return 0; //no supports possible
  }

  //variation on get_sumsupport to do a - b*bmult = c
  template<typename VarType1>
  BOOL get_diffsupport(VarRef3& a, VarType1& b, SysInt bmult, DomainInt c, SysInt (&resArr)[2]) {
    DomainInt bMin = b.getMin();
    DomainInt bMax = b.getMax();
    DomainInt aCurr = max(a.getMin(), (c + bMin*bmult));
    DomainInt aMax = min(a.getMax(), (c + bMax*bmult));
    while(aCurr <= aMax && !a.inDomain(aCurr))
      aCurr++;
    while(aCurr <= aMax) {
      DomainInt required = aCurr - c;
      DomainInt reqB = required / bmult;
      DomainInt modB = required % bmult;
      if(modB == 0 && b.inDomain(reqB)) {
        resArr[0] = aCurr;
        resArr[1] = reqB;
        D_ASSERT(resArr[0] - resArr[1]*bmult == c);
        return 1;
      } else {
        aCurr++;
        while(aCurr <= aMax && !(a.inDomain(aCurr)))
          aCurr++;
      }
    }
    return 0; //no supports possible    
  }

  //a little data structure we maintain per WL
  struct WLdata {
    SysInt other; //sequence number of the other WL helping to support the varval
    SysInt isForX : 1; //the supported var
    SysInt isForY : 1; //NB. the supported val is stored using dt->trigger_info()
    SysInt isForZ : 1;
  };

  //a mapping from WL number to struct of data
  vector<WLdata> wlToData;
  
  // find a couple of supports for every single value, put watches on
  // these, also build data structures to allow mapping from WL number
  // to var/val as well as the other WL on this value
  virtual void full_propagate()
  {

    //start placing WLs in the right places
    DynamicTrigger* dt = dynamic_trigger_start();  

    SysInt index = 0;
    WLdata workingData;
    SysInt supp[2] = {0, 0};

    wlToData.reserve(dynamic_trigger_count());

    //place a WL for each value
    //z first:
    DomainInt max = z.getMax();
    for(DomainInt i = z.getMin(); i <= max; i++) {
      if(z.inDomain(i)) {
    if(!get_sumsupport(x, y, i, supp)) {
      z.removeFromDomain(i);
    } else {
      x.addDynamicTrigger(dt, DomainRemoval, supp[0]);
      y.addDynamicTrigger(dt+1, DomainRemoval, supp[1]);
      dt->trigger_info() = i;     //keep a note of the value it supports
      (dt+1)->trigger_info() = i;
      workingData.other = index + 1;
      workingData.isForX = 0; workingData.isForY = 0; workingData.isForZ = 1;
      wlToData.push_back(workingData);
      workingData.other = index;
      wlToData.push_back(workingData);
      index += 2;
      dt += 2;
    }
      }
    }
    //supports for x:
    max = x.getMax();
    for(DomainInt i = x.getMin(); i <= max; i++) {
      if(x.inDomain(i)) {
    if(!get_diffsupport(z, y, ymult, i*xmult, supp)) {
      x.removeFromDomain(i);
    } else {
      z.addDynamicTrigger(dt, DomainRemoval, supp[0]);
      y.addDynamicTrigger(dt + 1, DomainRemoval, supp[1]);
      dt->trigger_info() = i;
      (dt+1)->trigger_info() = i;
      workingData.other = index + 1;
      workingData.isForX = 1; workingData.isForY = 0; workingData.isForZ = 0;
      wlToData.push_back(workingData);
      workingData.other = index;
      wlToData.push_back(workingData);
      index += 2;
      dt += 2;
    }
      }
    }
    //supports for y:
    max = y.getMax();
    for(DomainInt i = y.getMin(); i <= max; i++) {
      if(y.inDomain(i)) {
    if(!get_diffsupport(z, x, xmult, i*ymult, supp)) {
      y.removeFromDomain(i);
    } else {
      z.addDynamicTrigger(dt, DomainRemoval, supp[0]);
      x.addDynamicTrigger(dt + 1, DomainRemoval, supp[1]);
      dt->trigger_info() = i;
      (dt+1)->trigger_info() = i;
      workingData.other = index + 1;
      workingData.isForX = 0; workingData.isForY = 1; workingData.isForZ = 0;
      wlToData.push_back(workingData);
      workingData.other = index;
      wlToData.push_back(workingData);
      index += 2;
      dt += 2;
    }
      }
    }
  }

  virtual void propagate(DynamicTrigger* dt)
  {
    SysInt value = dt->trigger_info(); //the value formerly supported by dt
    DynamicTrigger* dts = dynamic_trigger_start();
    SysInt wl_no = dt - dts; //sequence number of WL
    WLdata data = wlToData[wl_no];
    if(!(data.isForX ? x.inDomain(value) :
     (data.isForY ? y.inDomain(value) : z.inDomain(value))))
      return;
    SysInt supp[2] = {0, 0};
    DynamicTrigger* first_dt = (dt < dts + data.other) ? dt : dt - 1;
    if(data.isForX) {
      if(!get_diffsupport(z, y, ymult, value*xmult, supp)) {
    x.removeFromDomain(value);
      } else {
    z.addDynamicTrigger(first_dt, DomainRemoval, supp[0]);
    y.addDynamicTrigger(first_dt + 1, DomainRemoval, supp[1]);
      }
    } else if(data.isForY) {
      if(!get_diffsupport(z, x, xmult, value*ymult, supp)) {
    y.removeFromDomain(value);
      } else {
    z.addDynamicTrigger(first_dt, DomainRemoval, supp[0]);
    x.addDynamicTrigger(first_dt + 1, DomainRemoval, supp[1]);
      }
    } else {
      if(!get_sumsupport(x, y, value, supp)) {
    z.removeFromDomain(value);
      } else {
    x.addDynamicTrigger(first_dt, DomainRemoval, supp[0]);
    y.addDynamicTrigger(first_dt + 1, DomainRemoval, supp[1]);
      }
    }
  }
};

template<typename VarArray, typename Var>
AbstractConstraint*
BuildCT_GACSUM(StateObj* stateObj, const vector<DomainInt>& consts, const VarArray& _var_array, const Var& var, ConstraintBlob&)
{
  typedef typename VarArray::value_type ValT;
  typedef typename Var::value_type VarT;
  
  if(consts.size() != 2 || _var_array.size() != 2)
    INPUT_ERROR("gacsum only accepts two variables on the left hand side.");

  return new SumEqConstraintDynamic<ValT,ValT,VarT>(stateObj, 
   consts[0], consts[1], _var_array[0], _var_array[1], var[0]);
}

#endif
