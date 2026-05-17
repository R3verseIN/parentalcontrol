#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
import glob
import os

def run_preflight_checks():
    print("==================================================")
    print("🔍 Running Local Pre-Flight XML Validation Checks...")
    print("==================================================")
    
    success = True
    # Find all XML files recursively inside the res/ directory
    resource_files = glob.glob('app/src/main/res/**/*.xml', recursive=True)
    
    if not resource_files:
        print("⚠️ Warning: No XML resource files found to validate.")
        return True
        
    for xml_file in resource_files:
        try:
            # Local fast parser execution
            ET.parse(xml_file)
        except ET.ParseError as e:
            print(f"❌ Syntax Error found in: {xml_file}")
            print(f"   Details: {e}")
            print("==================================================")
            success = False
        except Exception as e:
            print(f"⚠️ Unexpected error reading {xml_file}: {e}")
            
    if success:
        print("✅ Pre-flight checks passed! No XML syntax errors.")
        print("==================================================")
        return True
    else:
        print("🛑 Build Aborted. Please fix the XML syntax errors above.")
        print("==================================================")
        return False

if __name__ == '__main__':
    if not run_preflight_checks():
        sys.exit(1)
