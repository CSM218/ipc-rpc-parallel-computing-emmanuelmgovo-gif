#!/usr/bin/env python3
"""Simple local test runner that doesn't require gradle compilation"""

import sys
import os
import subprocess
from pathlib import Path

# Add test directory to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'autograder', 'tests'))

# Import test classes
from test_rpc_basic import AutograderTest
from test_parallel_execution import ParallelExecutionTest
from test_failure_handling import FailureHandlingTest
from test_protocol_structure import ProtocolStructureTest
from test_concurrency import ConcurrencyTest
from test_advanced_protocol import AdvancedProtocolTest

class SimpleLocalTester:
    def __init__(self):
        self.total_weight = 0
        self.total_score = 0
        
    def run_all(self):
        """Run all static tests (no compilation needed)"""
        
        os.chdir(os.path.dirname(os.path.abspath(__file__)))
        
        test_suites = [
            ("RPC", AutograderTest()),
            ("Parallel", ParallelExecutionTest()),
            ("Failure", FailureHandlingTest()),
            ("Protocol", ProtocolStructureTest()),
            ("Concurrency", ConcurrencyTest()),
            ("Advanced", AdvancedProtocolTest()),
        ]
        
        print("\n=== CSM218 Local Autograder (Static Tests) ===\n")
        
        all_passed = 0
        all_total = 0
        
        for suite_name, tester in test_suites:
            print(f"\n--- {suite_name} ---")
            results = tester.run_all()
            
            for test_name, result in results.items():
                passed = result.get("passed", False)
                weight = result.get("weight", 0)
                message = result.get("message", "")
                
                status = "✓ PASS" if passed else "✗ FAIL"
                print(f"[{status}] {test_name}: {message}")
                
                all_total += 1
                if passed:
                    all_passed += 1
                    self.total_score += weight
                self.total_weight += weight
        
        # Calculate score
        if self.total_weight > 0:
            percentage = (self.total_score / self.total_weight) * 100
        else:
            percentage = 0
        
        print(f"\n{'='*50}")
        print(f"Local Score: {percentage:.1f}%")
        print(f"Passed: {all_passed}/{all_total} tests")
        print(f"{'='*50}\n")
        
        return percentage

if __name__ == "__main__":
    tester = SimpleLocalTester()
    score = tester.run_all()
    
    if score < 60:
        sys.exit(1)
