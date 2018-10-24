#ifndef _BLOCK_CACHE_H_CDSHUICDS
#define _BLOCK_CACHE_H_CDSHUICDS

#include <vector>
#include <stdlib.h>

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

struct BlockCache
{
  std::vector<char*> blocks;
  
  BlockCache(int size)
  { blocks.resize(size); }
  
  char* do_malloc(size_t size)
  {
    if(blocks.empty())
      return static_cast<char*>(malloc(size));
    else
    {
      char* ret = blocks.back();
      blocks.pop_back();
      return static_cast<char*>(realloc(ret, size));
    }
  }
  
  void do_free(char* ptr)
  {
    if(blocks.size() == blocks.capacity())
      free(ptr);
    else
      blocks.push_back(ptr);
  }
  
  ~BlockCache()
  { for(size_t i = 0; i < blocks.size(); ++i) free(blocks[i]); }
};

#endif
