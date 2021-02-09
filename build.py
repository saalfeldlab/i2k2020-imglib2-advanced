#!/usr/bin/env python

import os
import sys
import subprocess
sys.dont_write_bytecode = True

def run_build(base_folder, build_args=[]):
	cmd_args = ['mvn', 'package'] + build_args
	subprocess.call(cmd_args, cwd=base_folder)

if __name__ == '__main__':
	base_folder = os.path.dirname(os.path.abspath(__file__))
	build_args = ['-P', 'fatjar,spark-provided']
	run_build(base_folder, build_args)
