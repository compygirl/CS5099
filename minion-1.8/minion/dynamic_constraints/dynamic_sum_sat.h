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

#ifndef CONSTRAINT_DYNAMIC_SUM_SAT_H
#define CONSTRAINT_DYNAMIC_SUM_SAT_H

template<typename VarArray>
struct BoolSATConstraintDynamic : public AbstractConstraint
{
  virtual string constraint_name()
  { return "watchsumgeq"; }
  
  virtual AbstractConstraint* reverse_constraint()
  {
    return new BoolLessSumConstraintDynamic<VarArray, DomainInt, 1>
               (stateObj, var_array, var_array.size());
  }

  typedef typename VarArray::value_type VarRef;
  
  CONSTRAINT_ARG_LIST2(var_array, (DomainInt)1);

  VarArray var_array;

  SysInt last;
  
  BoolSATConstraintDynamic(StateObj* _stateObj, const VarArray& _var_array) :
    AbstractConstraint(_stateObj), var_array(_var_array)
  { 
#ifndef DYNAMICTRIGGERS
    cerr << "This almost certainly isn't going to work... sorry" << endl;
#endif
    last = 0;
  }
  
  virtual SysInt dynamic_trigger_count()
  {
      return 2;
  }
    
  virtual void full_propagate()
  {
    DynamicTrigger* dt = dynamic_trigger_start();
    
    SysInt array_size = var_array.size(); 
    SysInt trig1, trig2;
    SysInt index = 0;
    
    while(index < array_size && !var_array[index].inDomain(1))
      ++index;
    
    trig1 = index;

    if(index == array_size)
    { // Not enough triggers
      getState(stateObj).setFailed(true);
      return;
    }
    
    ++index;
    
    while(index < array_size && !var_array[index].inDomain(1))
      ++index;
    
    trig2 = index;
    
    if(index >= array_size)
    { // Only one valid variable.
      var_array[trig1].propagateAssign(1);
      return;
    }
    
    dt->trigger_info() = trig1;
    var_array[trig1].addDynamicTrigger(dt, UpperBound);
    
    ++dt;
    
    dt->trigger_info() = trig2;
    var_array[trig2].addDynamicTrigger(dt, UpperBound);
    
    return;
  }
    
  virtual void propagate(DynamicTrigger* dt)
  {
    PROP_INFO_ADDONE(DynSumSat);
    SysInt var_size = var_array.size();
    
    DynamicTrigger* base_dt = dynamic_trigger_start();
    SysInt other_propval;
    
    if(base_dt == dt)
      other_propval = (base_dt + 1)->trigger_info();
    else
      other_propval = base_dt->trigger_info();
    
// I thought this would make the code go faster. But it doesn't!
//  if(var_array[other_propval].isAssignedValue(1))
//    return;
    

    bool found_new_support = false;

    SysInt loop = last;
    
    while(loop < var_size && !found_new_support)
    {
      if(loop != other_propval && var_array[loop].inDomain(1))
        found_new_support = true;
      else
        ++loop;
    }
    
    
    if(!found_new_support)
    {
      loop = 0;
      
      while(loop < last && !found_new_support)
      {
        if(loop != other_propval && var_array[loop].inDomain(1))
          found_new_support = true;
        else
          ++loop;
      }
    
      if(!found_new_support)
      {  // Have to propagate!
        var_array[other_propval].propagateAssign(1);
        return;
      }
    }
    
    // Found new value to watch
    dt->trigger_info() = loop;
    last = loop;
    var_array[loop].addDynamicTrigger(dt, UpperBound);
  }
  
  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == (SysInt)var_array.size());
    SysInt count = 0;
    for(SysInt i = 0; i < v_size; ++i)
      count += (v[i] == 1);
    return count > 0;
  }
  
  virtual vector<AnyVarRef> get_vars()
  { 
    vector<AnyVarRef> vars;
    vars.reserve(var_array.size());
    for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
      vars.push_back(AnyVarRef(var_array[i]));
    return vars;  
  }
  
  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
    for(SysInt i = 0; i < (SysInt)var_array.size(); ++i)
    {
      if(var_array[i].inDomain(1))
      {
        assignment.push_back(make_pair(i, 1));
        return true;
      }
    }
    return false;
  }
};

template<typename VarArray>
AbstractConstraint*
BoolSATConDynamic(StateObj* stateObj, const VarArray& _var_array)
{ return new BoolSATConstraintDynamic<VarArray>(stateObj, _var_array); }

#endif
