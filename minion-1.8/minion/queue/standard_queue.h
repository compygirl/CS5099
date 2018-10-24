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

#ifndef STANDARD_QUEUE_H
#define STANDARD_QUEUE_H

// use a deque (as a fifo) for the special queue
#define SQ_DEQUE

#ifdef SQ_DEQUE
#define QueueCon deque
#define queueTop front
#define queuePop pop_front
#else
#define QueueCon vector
#define queueTop back
#define queuePop pop_back
#endif

#include <deque>
#include "../solver.h"
#include "../get_info/get_info.h"
#include "../constraints/triggers.h"
#include "../constraints/constraint_abstract.h"
#include "TriggerBacktrackQueue.h"
#ifdef WEIGHTED_TRIGGERS
#include <queue>
#endif

class Queues
{
  StateObj* stateObj;


#ifdef WEIGHTED_TRIGGERS
  priority_queue<TriggerRange> propagate_trigger_list;
#else
  QueueCon<TriggerRange> propagate_trigger_list;
#endif
  QueueCon<DynamicTrigger*> dynamic_trigger_list;

  // Special triggers are those which can only be run while the
  // normal queue is empty. This list is at the moment only used
  // by reified constraints when they want to start propagation.
  // I don't like it, but it is necesasary.
  QueueCon<AbstractConstraint*> special_triggers;


  TriggerBacktrackQueue tbq;
public:

  TriggerBacktrackQueue& getTbq()
  { return tbq; }


  Queues(StateObj* _stateObj) : stateObj(_stateObj)
  {}

  void pushSpecialTrigger(AbstractConstraint* trigger)
  {
      CON_INFO_ADDONE(AddSpecialToQueue);
      special_triggers.push_back(trigger);
  }


  inline void pushTriggers(TriggerRange new_triggers)
  {
    CON_INFO_ADDONE(AddConToQueue);
#ifdef WEIGHTED_TRIGGERS
    propagate_trigger_list.push(new_triggers);
#else
    propagate_trigger_list.push_back(new_triggers);
#endif
  }

  void pushDynamicTriggers(DynamicTrigger* new_dynamic_trig_range)
  {
    CON_INFO_ADDONE(AddDynToQueue);
    D_ASSERT(new_dynamic_trig_range->sanity_check_list());
    dynamic_trigger_list.push_back(new_dynamic_trig_range);
  }


  void clearQueues()
  {
#ifdef WEIGHTED_TRIGGERS
    while(!propagate_trigger_list.empty()) {
      propagate_trigger_list.pop();
    }
#else
    propagate_trigger_list.clear();
#endif
    dynamic_trigger_list.clear();

    if(!special_triggers.empty())
    {
      SysInt size = special_triggers.size();
      for(SysInt i = 0; i < size; ++i)
        special_triggers[i]->special_unlock();
      special_triggers.clear();
    }
  }

  bool isQueuesEmpty()
  {
    return propagate_trigger_list.empty() && dynamic_trigger_list.empty() &&
    special_triggers.empty();
  }


  // next_queue_ptr is defined in constraint_dynamic.
  // It is used if pointers are moved around.

  // Subclass this class and change the following three methods.

  bool propagateDynamicTriggerLists()
  {
    bool* fail_ptr = getState(stateObj).getFailedPtr();
    while(!dynamic_trigger_list.empty())
    {
      DynamicTrigger* t = dynamic_trigger_list.queueTop();
      dynamic_trigger_list.queuePop();
      DynamicTrigger* it = t->next;

      while(it != t)
      {
        if(*fail_ptr)
        {
          clearQueues();
          return true;
        }

#ifdef MINION_DEBUG
        DynamicTrigger dummy((AbstractConstraint*)(BAD_POINTER));
#else
        DynamicTrigger dummy;
#endif
        dummy.add_after(it);

        CON_INFO_ADDONE(DynamicTrigger);
        it->propagate();

#ifdef WDEG
        if(*fail_ptr)
          it->constraint->incWdeg();
#endif

       it = dummy.next;
       releaseTrigger(stateObj, &dummy);
      }
    }
    return false;
  }

  bool propagateStaticTriggerLists()
  {
    bool* fail_ptr = getState(stateObj).getFailedPtr();
    while(!propagate_trigger_list.empty())
    {
#ifdef WEIGHTED_TRIGGERS
      TriggerRange t = propagate_trigger_list.top();
      DomainInt data_val = t.data;
      propagate_trigger_list.pop();
#else
      TriggerRange t = propagate_trigger_list.queueTop();
      DomainInt data_val = t.data;
      propagate_trigger_list.queuePop();
#endif

      for(Trigger* it = t.begin(); it != t.end(); it++)
      {
        if(*fail_ptr)
        {
          clearQueues();
          return true;
        }

#ifndef NO_DEBUG
        if(getOptions(stateObj).fullpropagate)
          it->full_propagate();
        else
        {
          CON_INFO_ADDONE(StaticTrigger);
          it->propagate(data_val);
        }
#else
        {
          CON_INFO_ADDONE(StaticTrigger);
          it->propagate(data_val);
        }
#endif
#ifdef WDEG
        if(*fail_ptr)
          it->constraint->incWdeg();
#endif
      }
    }

    return false;
  }

  inline void propagateQueue()
  {
    while(true)
    {
      if (getState(stateObj).isDynamicTriggersUsed())
      {
        while(!propagate_trigger_list.empty() || !dynamic_trigger_list.empty())
        {
          if(propagateDynamicTriggerLists())
            return;

          /* Don't like code duplication here but a slight efficiency gain */
          if(propagateStaticTriggerLists())
            return;
        }
      }
      else
      {
        if(propagateStaticTriggerLists())
          return;
      }

      if(special_triggers.empty())
        return;

        AbstractConstraint* trig = special_triggers.queueTop();
        special_triggers.queuePop();

      CON_INFO_ADDONE(SpecialTrigger);
      trig->special_check();
#ifdef WDEG
      if(getState(stateObj).isFailed()) trig->incWdeg();
#endif

      if(getState(stateObj).isFailed())
      {
        clearQueues();
        return;
      }
    } // while(true)

  } // end Function

// ******************************************************************************************
// Second copy of the propagate queue methods, adapted for the root node only.

  bool propagateDynamicTriggerListsRoot()
  {
    bool* fail_ptr = getState(stateObj).getFailedPtr();
    while(!dynamic_trigger_list.empty())
    {
      DynamicTrigger* t = dynamic_trigger_list.queueTop();
      dynamic_trigger_list.queuePop();
      DynamicTrigger* it = t->next;

      while(it != t)
      {
        if(*fail_ptr)
        {
          clearQueues();
          return true;
        }

#ifdef MINION_DEBUG
        DynamicTrigger dummy((AbstractConstraint*)(BAD_POINTER));
#else
        DynamicTrigger dummy;
#endif
        dummy.add_after(it);

        if(it->constraint->full_propagate_done)
        {
            CON_INFO_ADDONE(DynamicTrigger);
            it->propagate();
        }

        it = dummy.next;
        releaseTrigger(stateObj, &dummy);
      }
    }
    return false;
  }

  bool propagateStaticTriggerListsRoot()
  {
    bool* fail_ptr = getState(stateObj).getFailedPtr();
    while(!propagate_trigger_list.empty())
    {
#ifdef WEIGHTED_TRIGGERS
      TriggerRange t = propagate_trigger_list.top();
      DomainInt data_val = t.data;
      propagate_trigger_list.pop();
#else
      TriggerRange t = propagate_trigger_list.queueTop();
      DomainInt data_val = t.data;
      propagate_trigger_list.queuePop();
#endif

      for(Trigger* it = t.begin(); it != t.end(); it++)
      {
        if(*fail_ptr)
        {
          clearQueues();
          return true;
        }
        if(it->constraint->full_propagate_done)
        {
#ifndef NO_DEBUG
        if(getOptions(stateObj).fullpropagate)
          it->full_propagate();
        else
        {
          CON_INFO_ADDONE(StaticTrigger);
          it->propagate(data_val);
        }
#else
        {
          CON_INFO_ADDONE(StaticTrigger);
          it->propagate(data_val);
        }
#endif
        }
      }
    }

    return false;
  }

  inline void propagateQueueRoot()
  {
    while(true)
    {
      if (getState(stateObj).isDynamicTriggersUsed())
      {
        while(!propagate_trigger_list.empty() || !dynamic_trigger_list.empty())
        {
          if(propagateDynamicTriggerListsRoot())
            return;

          /* Don't like code duplication here but a slight efficiency gain */
          if(propagateStaticTriggerListsRoot())
            return;
        }
      }
      else
      {
        if(propagateStaticTriggerListsRoot())
          return;
      }

      if(special_triggers.empty())
        return;

      AbstractConstraint* trig = special_triggers.queueTop();
      special_triggers.queuePop();
      CON_INFO_ADDONE(SpecialTrigger);
      trig->special_check();

      if(getState(stateObj).isFailed())
      {
        clearQueues();
        return;
      }

    } // while(true)

  } // end Function
};

// This just allows SAC (which wants a list of vars)
// and normal propagate to have the same input method.
// Just checking the bounds doesn't make sense here, so we ignore it.
//template<typename Vars>
//inline void propagate_queue_vars(StateObj* stateObj, Vars& vars, bool /*CheckBounds*/)
//{ getQueue(stateObj).propagateQueue(); }

#endif
