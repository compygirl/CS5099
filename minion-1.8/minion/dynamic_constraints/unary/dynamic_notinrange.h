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

/** @help constraints;w-notinrange Description
  The constraint w-notinrange(x, [a,b]) ensures that x < a or b < x.
*/

/** @help constraints;w-notinrange References
  See also

  help constraints w-inrange
*/

#ifndef CONSTRAINT_DYNAMIC_UNARY_NOTINRANGE_H
#define CONSTRAINT_DYNAMIC_UNARY_NOTINRANGE_H

#include "dynamic_inrange.h"

// Checks if a variable is in a fixed Range.
template<typename Var>
  struct WatchNotInRangeConstraint : public AbstractConstraint
{
  virtual string constraint_name()
    { return "w-notinrange"; }

  CONSTRAINT_ARG_LIST2(var, make_vec(range_min, range_max));
  Var var;

  DomainInt range_min;
  DomainInt range_max;

  template<typename T>
  WatchNotInRangeConstraint(StateObj* _stateObj, const Var& _var, const T& _vals) :
  AbstractConstraint(_stateObj), var(_var)
  { 
    if(_vals.size() != 2)
    {
      cerr << "The range of an 'NotInRange' constraint must contain 2 values!" << endl;
      abort();
    }
    
    range_min = _vals[0];
    range_max = _vals[1];
  }

  virtual SysInt dynamic_trigger_count()
    { return 2; }

  virtual void full_propagate()
  {  
    DynamicTrigger* dt = dynamic_trigger_start();    
    // Ignore empty ranges
    if(range_min > range_max)
      return;
      
    if(var.getMax() <= range_max)
    {
      var.setMax(range_min - 1);
      return;
    }
    
    if(var.getMin() >= range_min)
    {
      var.setMin(range_max + 1);
      return;
    }
    
    if(var.isBound())
    {
      var.addDynamicTrigger(dt, DomainChanged);
      propagate(NULL); 
    }
    else
    {
      for(DomainInt i = range_min; i <= range_max; ++i)
        var.removeFromDomain(i); 
    }
  }


  virtual void propagate(DynamicTrigger* dt)
  {
    PROP_INFO_ADDONE(WatchNotInRange);
    D_ASSERT(var.isBound());
    
    if(var.getMax() <= range_max)
    {
      var.setMax(range_min - 1);
      return;
    }
    
    if(var.getMin() >= range_min)
    {
      var.setMin(range_max + 1);
      return;
    }
  }

  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == 1);
    return (v[0] < range_min || v[0] > range_max);
  }

  virtual vector<AnyVarRef> get_vars()
  { 
    vector<AnyVarRef> vars;
    vars.reserve(1);
    vars.push_back(var);
    return vars;
  }

  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {  
    /// TODO: Make faster
    if(var.getMin() < range_min || var.getMin() > range_max)
    {
      assignment.push_back(make_pair(0, var.getMin()));
      return true;
    }
    
    if(var.getMax() < range_min || var.getMax() > range_max)
    {
      assignment.push_back(make_pair(0, var.getMax()));
      return true;
    }
    return false;
  }

  virtual AbstractConstraint* reverse_constraint()
  {
      std::array<DomainInt, 2> a = {range_min, range_max};
      return new WatchInRangeConstraint<Var>(stateObj, var, a);
  }
};

// From dynamic_inrange.h
template<typename Var>
AbstractConstraint* WatchInRangeConstraint<Var>::reverse_constraint()
{
    std::array<DomainInt, 2> a = {range_min, range_max};
    return new WatchNotInRangeConstraint<Var>(stateObj, var, a);  
}
#endif
