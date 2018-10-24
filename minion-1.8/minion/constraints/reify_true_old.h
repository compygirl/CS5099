/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 

   $Id$
*/

/* Minion
* Copyright (C) 2006
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

#ifndef REIFY_TRUE_OLD_H
#define REIFY_TRUE_OLD_H

#include "constraint_abstract.h"
#include "../minion.h"
#include "../get_info/get_info.h"
#include "../queue/standard_queue.h"
#include "../dynamic_constraints/old_dynamic_reifyimply.h"
#include "../dynamic_constraints/dynamic_new_and.h"
#include "../dynamic_constraints/unary/dynamic_literal.h"

template<typename BoolVar>
struct reify_true_old : public AbstractConstraint
{
  virtual string extended_name()
  { return constraint_name() + ":" + poscon->extended_name(); }

  virtual string constraint_name()
  { return "reifyimply-old"; }
  
    CONSTRAINT_ARG_LIST2(poscon, rar_var);

  AbstractConstraint* poscon;
  BoolVar rar_var;
  bool constraint_locked;
  
  Reversible<bool> full_propagate_called;
  
  reify_true_old(StateObj* _stateObj, AbstractConstraint* _poscon, BoolVar v) : AbstractConstraint(_stateObj), poscon(_poscon), 
                                                                            rar_var(v), constraint_locked(false),
                                                                            full_propagate_called(stateObj, false)
  { }
  
  // (var -> C) is equiv to (!var \/ C), so reverse is (var /\ !C)
  virtual AbstractConstraint* reverse_constraint()
  { 
    vector<AbstractConstraint*> con;
    con.push_back(new WatchLiteralConstraint<BoolVar>(stateObj, rar_var, 1));
    con.push_back(poscon->reverse_constraint());
    return new Dynamic_AND(stateObj, con);
  }

  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    
    DomainInt back_val = *(v + checked_cast<SysInt>(v_size - 1));
    //v.pop_back();
    if(back_val != 0)
      return poscon->check_assignment(v, v_size - 1);
    else
      return true;
  }
  
  virtual vector<AnyVarRef> get_vars()
  { 
    vector<AnyVarRef> vec = poscon->get_vars();
    vec.push_back(rar_var);
    return vec;
  }
  
  virtual triggerCollection setup_internal()
  {
    triggerCollection postrig = poscon->setup_internal();
    triggerCollection triggers;
    for(UnsignedSysInt i=0;i<postrig.size();i++)
    {
      postrig[i]->trigger.constraint = this;
      D_ASSERT(postrig[i]->trigger.info != -99999);
      triggers.push_back(postrig[i]);
    }
    triggers.push_back(make_trigger(rar_var, Trigger(this, -99999), LowerBound));
    return triggers;
  }
  
  virtual void special_check()
  {
    D_ASSERT(constraint_locked);
    constraint_locked = false;
    poscon->full_propagate();
    full_propagate_called = true;
  }
  
  virtual void special_unlock()
  {
    D_ASSERT(constraint_locked);
    constraint_locked = false;
  }
  
  virtual void propagate(DomainInt i_in, DomainDelta domain)
  {
    const SysInt i = checked_cast<SysInt>(i_in);
    PROP_INFO_ADDONE(ReifyTrue);
    if(constraint_locked)
      return;

    if(i == -99999)
    {
      constraint_locked = true;
      getQueue(stateObj).pushSpecialTrigger(this);
      return;
    }
    
    if(full_propagate_called)
    {
      D_ASSERT(rar_var.isAssigned() && rar_var.getAssignedValue() == 1);
      poscon->propagate(i, domain);
      return;
    }
    
    if(!rar_var.isAssigned()) { //don't check unsat if rar_var=0
#ifdef MINION_DEBUG
      bool flag;
      GET_ASSIGNMENT(assignment0, poscon);
      bool unsat = poscon->check_unsat(i, domain);
      D_ASSERT((!flag && unsat) || (flag && !unsat));
#endif
      PROP_INFO_ADDONE(ReifyImplyCheckUnsat);
      if(poscon->check_unsat(i, domain)) 
    { rar_var.propagateAssign(false); }
    }
  }
  
  virtual void full_propagate()
  {
    #ifdef MINION_DEBUG
    {
      bool flag;
      GET_ASSIGNMENT(assignment0, poscon);
      bool unsat = poscon->full_check_unsat();
      D_ASSERT((!flag && unsat) || (flag && !unsat));
    }
    #endif
    
    PROP_INFO_ADDONE(ReifyImplyFullCheckUnsat);
    if(poscon->full_check_unsat())
      rar_var.propagateAssign(false);

    if(rar_var.isAssigned() && rar_var.getAssignedValue() > 0)
    {
      poscon->full_propagate();
      full_propagate_called = true;
    }
  }
};

// From dynamic_reifyimply.h
template<typename BoolVar>
AbstractConstraint*
truereifyConDynamicOld(StateObj* stateObj, AbstractConstraint* c, BoolVar var);

template<typename BoolVar>
AbstractConstraint*
truereifyConOld(StateObj* stateObj, AbstractConstraint* c, BoolVar var)
{ 
  if(c->dynamic_trigger_count() == 0)
    return new reify_true_old<BoolVar>(stateObj, &*c, var); 
  else
    return truereifyConDynamicOld(stateObj, c, var);
}
#endif
