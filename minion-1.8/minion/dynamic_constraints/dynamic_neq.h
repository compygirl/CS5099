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

#ifndef CONSTRAINT_DYNAMIC_NEQ_H
#define CONSTRAINT_DYNAMIC_NEQ_H

#include "../constraints/constraint_equal.h"

template<typename Var1, typename Var2>
struct WatchNeqConstraint : public AbstractConstraint
{
  virtual string constraint_name()
  { return "watchneq"; }
  
  Var1 var1;
  Var2 var2;

  CONSTRAINT_ARG_LIST2(var1, var2);

  WatchNeqConstraint(StateObj* _stateObj, const Var1& _var1, const Var2& _var2) :
    AbstractConstraint(_stateObj), var1(_var1), var2(_var2)
  { 
    CheckNotBoundSingle(var1, "watchneq","neq");
  }
  
  virtual SysInt dynamic_trigger_count()
  { return 2; }
  
  virtual void full_propagate()
  {  
    DynamicTrigger* dt = dynamic_trigger_start();
    
      if(var1.isAssigned() && var2.isAssigned() && var1.getAssignedValue() == var2.getAssignedValue())
      {
        getState(stateObj).setFailed(true);
        return;
      }
      
      if(var1.isAssigned())
      {
        var2.removeFromDomain(var1.getAssignedValue());
        return;
      }
      
      if(var2.isAssigned())
      {
        var1.removeFromDomain(var2.getAssignedValue());
        return;
      }
      
    var1.addDynamicTrigger(dt    , Assigned);
    var2.addDynamicTrigger(dt + 1, Assigned);
  }
  
    
  virtual void propagate(DynamicTrigger* dt)
  {
      PROP_INFO_ADDONE(WatchNEQ);
      DynamicTrigger* dt_start = dynamic_trigger_start();
      
    D_ASSERT(dt == dt_start || dt == dt_start + 1);
    
      if(dt == dt_start)
      {
        D_ASSERT(var1.isAssigned());
        var2.removeFromDomain(var1.getAssignedValue());
      }
      else
      {
        D_ASSERT(var2.isAssigned());
        var1.removeFromDomain(var2.getAssignedValue());
      }
  }
  
  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == 2);
    return v[0] != v[1];
  }
  
  virtual vector<AnyVarRef> get_vars()
  { 
    vector<AnyVarRef> vars;
      vars.reserve(2);
    vars.push_back(var1);
    vars.push_back(var2);
    return vars;
  }
  
  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
    if(var1.isAssigned() && var2.isAssigned() && var1.getAssignedValue() == var2.getAssignedValue())
      return false;
    
    if(var1.isAssigned())
    {
      assignment.push_back(make_pair(0, var1.getAssignedValue()));
      if(var2.getMin() != var1.getAssignedValue())
        assignment.push_back(make_pair(1, var2.getMin()));
      else
        assignment.push_back(make_pair(1, var2.getMax()));
    }
    else
    {
      assignment.push_back(make_pair(1, var2.getMin()));
      if(var1.getMin() != var2.getMin())
        assignment.push_back(make_pair(0, var1.getMin()));
      else
        assignment.push_back(make_pair(0, var1.getMax()));
    }
    return true;
  }

  virtual AbstractConstraint* reverse_constraint()
  { return new EqualConstraint<Var1,Var2>(stateObj, var1, var2); }
};
#endif
